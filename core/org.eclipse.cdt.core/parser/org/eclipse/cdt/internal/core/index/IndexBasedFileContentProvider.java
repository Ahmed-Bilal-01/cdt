/*******************************************************************************
 * Copyright (c) 2005, 2011 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     QNX - Initial API and implementation
 *     Markus Schorn (Wind River Systems)
 *     Andrew Ferguson (Symbian)
 *     Anton Leherbauer (Wind River Systems)
 *     IBM Corporation
 *     Sergey Prigogin (Google)
 *******************************************************************************/
package org.eclipse.cdt.internal.core.index;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.core.dom.ast.IFileNomination;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPUsingDirective;
import org.eclipse.cdt.core.index.IIndex;
import org.eclipse.cdt.core.index.IIndexFile;
import org.eclipse.cdt.core.index.IIndexFileLocation;
import org.eclipse.cdt.core.index.IIndexInclude;
import org.eclipse.cdt.core.index.IIndexMacro;
import org.eclipse.cdt.core.parser.IncludeFileContentProvider;
import org.eclipse.cdt.internal.core.parser.IMacroDictionary;
import org.eclipse.cdt.internal.core.parser.scanner.InternalFileContent;
import org.eclipse.cdt.internal.core.parser.scanner.InternalFileContent.InclusionKind;
import org.eclipse.cdt.internal.core.parser.scanner.InternalFileContentProvider;
import org.eclipse.cdt.internal.core.pdom.ASTFilePathResolver;
import org.eclipse.cdt.internal.core.pdom.AbstractIndexerTask;
import org.eclipse.cdt.internal.core.pdom.AbstractIndexerTask.IndexFileContent;
import org.eclipse.core.runtime.CoreException;

/**
 * Code reader factory, that fakes code readers for header files already stored in the index.
 */
public final class IndexBasedFileContentProvider extends InternalFileContentProvider {
	private static final class NeedToParseException extends Exception {}
	private static final String GAP = "__gap__"; //$NON-NLS-1$

	private final IIndex fIndex;
	private int fLinkage;
	/** The fall-back code reader factory used in case a header file is not indexed */
	private final InternalFileContentProvider fFallBackFactory;
	private final ASTFilePathResolver fPathResolver;
	private final AbstractIndexerTask fRelatedIndexerTask;
	private boolean fSupportFillGapFromContextToHeader;
	private long fFileSizeLimit= 0;

	private final Map<IIndexFileLocation, IFileNomination> fPragmaOnce= new HashMap<IIndexFileLocation, IFileNomination>();

	public IndexBasedFileContentProvider(IIndex index,
			ASTFilePathResolver pathResolver, int linkage, IncludeFileContentProvider fallbackFactory) {
		this(index, pathResolver, linkage, fallbackFactory, null);
	}

	public IndexBasedFileContentProvider(IIndex index, ASTFilePathResolver pathResolver, int linkage,
			IncludeFileContentProvider fallbackFactory, AbstractIndexerTask relatedIndexerTask) {
		fIndex= index;
		fFallBackFactory= (InternalFileContentProvider) fallbackFactory;
		fPathResolver= pathResolver;
		fRelatedIndexerTask= relatedIndexerTask;
		fLinkage= linkage;
	}

	public void setSupportFillGapFromContextToHeader(boolean val) {
		fSupportFillGapFromContextToHeader= val;
	}
	
	public void setFileSizeLimit(long limit) {
		fFileSizeLimit= limit;
	}

	public void setLinkage(int linkageID) {
		fLinkage= linkageID;
	}
	
	@Override
	public void resetPragmaOnceTracking() {
		fPragmaOnce.clear();
	}

	/** 
	 * Reports detection of pragma once semantics.
	 */
	@Override
	public void reportPragmaOnceSemantics(String filePath, IFileNomination nom) {
		fPragmaOnce.put(fPathResolver.resolveIncludeFile(filePath), nom);
	}

	/**
	 * Returns whether the given file has been included with pragma once semantics.
	 */
	@Override
	public IFileNomination isIncludedWithPragmaOnceSemantics(String filePath) {
		return fPragmaOnce.get(fPathResolver.resolveIncludeFile(filePath));
	}

	@Override
	public boolean getInclusionExists(String path) {
		return fPathResolver.doesIncludeFileExist(path); 
	}
	
	
	@Override
	public InternalFileContent getContentForInclusion(String path, IMacroDictionary macroDictionary) {
		IIndexFileLocation ifl= fPathResolver.resolveIncludeFile(path);
		if (ifl == null) {
			return null;
		}

		path= fPathResolver.getASTPath(ifl);
		try {
			IIndexFile file = selectIndexFile(macroDictionary, ifl);
			if (file != null) {
				try {
					List<IIndexFile> files= new ArrayList<IIndexFile>();
					List<IIndexMacro> macros= new ArrayList<IIndexMacro>();
					List<ICPPUsingDirective> directives= new ArrayList<ICPPUsingDirective>();
					Map<IIndexFileLocation, IFileNomination> newPragmaOnce= new HashMap<IIndexFileLocation, IFileNomination>();
					collectFileContent(file, null, newPragmaOnce, files, macros, directives, null);
					// Report pragma once inclusions, only if no exception was thrown.
					fPragmaOnce.putAll(newPragmaOnce);
					List<String> newPragmaOncePaths = toPathList(newPragmaOnce.keySet());
					return new InternalFileContent(path, macros, directives, files, newPragmaOncePaths);
				} catch (NeedToParseException e) {
				}
			} 
		} catch (CoreException e) {
			CCorePlugin.log(e);
		}

		// Skip large files
		if (fFileSizeLimit > 0 && fPathResolver.getFileSize(path) > fFileSizeLimit) {
			return new InternalFileContent(path, InclusionKind.SKIP_FILE, null);
		}

		if (fFallBackFactory != null) {
			InternalFileContent ifc= getContentForInclusion(ifl, path);
			if (ifc != null)
				ifc.setIsSource(fPathResolver.isSource(path));
			return ifc;
		}
		return null;
	}

	public List<String> toPathList(Collection<IIndexFileLocation> newPragmaOnce) {
		List<String> newPragmaOncePaths= new ArrayList<String>(newPragmaOnce.size());
		for (IIndexFileLocation l : newPragmaOnce) {
			newPragmaOncePaths.add(fPathResolver.getASTPath(l));
		}
		return newPragmaOncePaths;
	}

	public IIndexFile selectIndexFile(IMacroDictionary macroDictionary, IIndexFileLocation ifl)
			throws CoreException {
		if (fRelatedIndexerTask != null)
			return fRelatedIndexerTask.selectIndexFile(fLinkage, ifl, macroDictionary);
		
		for (IIndexFile file : fIndex.getFiles(fLinkage, ifl)) {
			if (macroDictionary.satisfies(file.getSignificantMacros()))
				return file;
		}
		return null;
	}

	@Override
	public InternalFileContent getContentForInclusion(IIndexFileLocation ifl, String astPath) {
		if (fFallBackFactory != null) {
			return fFallBackFactory.getContentForInclusion(ifl, astPath);
		}
		return null;
	}

	private boolean collectFileContent(IIndexFile file, IIndexFile stopAt, Map<IIndexFileLocation, IFileNomination> newPragmaOnce,
			List<IIndexFile> files, List<IIndexMacro> macros,
			List<ICPPUsingDirective> usingDirectives, Set<IIndexFile> preventRecursion)
			throws CoreException, NeedToParseException {
		if (file.equals(stopAt))
			return true;
		
		IIndexFileLocation ifl= file.getLocation();
		if (newPragmaOnce.containsKey(ifl))
			return false;
		if (file.hasPragmaOnceSemantics()) 
			newPragmaOnce.put(ifl, file);
		
		if (preventRecursion != null) {
			if (fPragmaOnce.containsKey(ifl)) 
				return false;
		} else {
			preventRecursion= new HashSet<IIndexFile>();
		}
		if (!preventRecursion.add(file))
			return false;

		final ICPPUsingDirective[] uds;
		final Object[] pds;
		if (fRelatedIndexerTask != null) {
			IndexFileContent content= fRelatedIndexerTask.getFileContent(fLinkage, ifl, file);
			if (content == null) 
				throw new NeedToParseException();
			uds= content.getUsingDirectives();
			pds= content.getPreprocessingDirectives();
		} else {
			uds= file.getUsingDirectives();
			pds= IndexFileContent.merge(file.getIncludes(), file.getMacros());
		}
		
		files.add(file);
		int udx= 0;
		for (Object d : pds) {
			if (d instanceof IIndexMacro) {
				macros.add((IIndexMacro) d);
			} else if (d instanceof IIndexInclude) {
				IIndexInclude inc= (IIndexInclude) d;
				IIndexFile includedFile= fIndex.resolveInclude((IIndexInclude) d);
				if (includedFile != null) {
					// Add in using directives that appear before the inclusion
					final int offset= inc.getNameOffset();
					for (; udx < uds.length && uds[udx].getPointOfDeclaration() <= offset; udx++) {
						usingDirectives.add(uds[udx]);
					}
					if (collectFileContent(includedFile, stopAt, newPragmaOnce, files, macros, usingDirectives, preventRecursion))
						return true;
				}
			}
		}
		// Add in remaining using directives
		for (; udx < uds.length; udx++) {
			usingDirectives.add(uds[udx]);
		}
		preventRecursion.remove(file);
		return false;
	}

	@Override
	public InternalFileContent getContentForContextToHeaderGap(String path,
			IMacroDictionary macroDictionary) {
		if (!fSupportFillGapFromContextToHeader) {
			return null;
		}
		
		IIndexFileLocation ifl= fPathResolver.resolveASTPath(path);
		if (ifl == null) {
			return null;
		}

		try {
			// TODO(197989) This is wrong, the dictionary at this point does not relate to the target
			// file. We'll have to provide the target file from the outside (i.e. from the indexer
			// task.
			IIndexFile targetFile = selectIndexFile(macroDictionary, ifl);
			if (targetFile == null) {
				return null;
			}

			IIndexFile contextFile= findContext(targetFile);
			if (contextFile == targetFile || contextFile == null) {
				return null;
			}
			
			Map<IIndexFileLocation, IFileNomination> newPragmaOnce= new HashMap<IIndexFileLocation, IFileNomination>();
			List<IIndexFile> filesIncluded= new ArrayList<IIndexFile>();
			ArrayList<IIndexMacro> macros= new ArrayList<IIndexMacro>();
			ArrayList<ICPPUsingDirective> directives= new ArrayList<ICPPUsingDirective>();
			try {
				if (!collectFileContent(contextFile, targetFile, newPragmaOnce,
						filesIncluded, macros, directives, new HashSet<IIndexFile>())) {
					return null;
				}
			} catch (NeedToParseException e) {
				return null;
			}

			// Report pragma once inclusions.
			fPragmaOnce.putAll(newPragmaOnce);
			List<String> newPragmaOncePaths = toPathList(newPragmaOnce.keySet());
			return new InternalFileContent(GAP, macros, directives, new ArrayList<IIndexFile>(filesIncluded),
					newPragmaOncePaths);
		} catch (CoreException e) {
			CCorePlugin.log(e);
		}
		return null;
	}

	private IIndexFile findContext(IIndexFile file) throws CoreException {
		final HashSet<IIndexFile> ifiles= new HashSet<IIndexFile>();
		ifiles.add(file);
		IIndexInclude include= file.getParsedInContext();
		while (include != null) {
			final IIndexFile context= include.getIncludedBy();
			if (!ifiles.add(context)) {
				return file;
			}
			file= context;
			include= context.getParsedInContext();
		}
		return file;
	}


	public IIndexFile[] findIndexFiles(InternalFileContent fc) throws CoreException {
		IIndexFileLocation ifl = fPathResolver.resolveASTPath(fc.getFileLocation());
		if (ifl != null) {
			return fIndex.getFiles(fLinkage, ifl);
		}
		return IIndexFile.EMPTY_FILE_ARRAY;
	}
}
