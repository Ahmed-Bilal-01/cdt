/*******************************************************************************
 * Copyright (c) 2004 QNX Software Systems and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Common Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors: QNX Software Systems - initial API and implementation
 ******************************************************************************/
package org.eclipse.cdt.internal.ui.dialogs.cpaths;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.cdt.core.model.ICProject;
import org.eclipse.cdt.core.model.IPathEntry;
import org.eclipse.cdt.internal.ui.util.PixelConverter;
import org.eclipse.cdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.cdt.internal.ui.wizards.dialogfields.IDialogFieldListener;
import org.eclipse.cdt.internal.ui.wizards.dialogfields.ITreeListAdapter;
import org.eclipse.cdt.internal.ui.wizards.dialogfields.LayoutUtil;
import org.eclipse.cdt.internal.ui.wizards.dialogfields.ListDialogField;
import org.eclipse.cdt.internal.ui.wizards.dialogfields.TreeListDialogField;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.widgets.Composite;

public class CPathContainerEntryPage extends CPathBasePage {

	private ListDialogField fCPathList;
	private ICProject fCurrJProject;

	private TreeListDialogField fContainersList;

	private final int IDX_ADD = 0;

	private final int IDX_EDIT = 2;
	private final int IDX_REMOVE = 3;

	public CPathContainerEntryPage(ListDialogField cPathList) {
		super(CPathEntryMessages.getString("ContainerEntryPage.title")); //$NON-NLS-1$
		fCPathList = cPathList;

		String[] buttonLabels = new String[]{
		/* IDX_ADD */CPathEntryMessages.getString("ContainerEntryPage.add.button"), //$NON-NLS-1$
				/* */null,
				/* IDX_EDIT */CPathEntryMessages.getString("ContainerEntryPage.edit.button"), //$NON-NLS-1$
				/* IDX_REMOVE */CPathEntryMessages.getString("ContainerEntryPage.remove.button") //$NON-NLS-1$
		};

		ContainersAdapter adapter = new ContainersAdapter();

		fContainersList = new TreeListDialogField(adapter, buttonLabels, new CPElementLabelProvider());
		fContainersList.setDialogFieldListener(adapter);
		fContainersList.setLabelText(CPathEntryMessages.getString("ContainerEntryPage.libraries.label")); //$NON-NLS-1$

		fContainersList.enableButton(IDX_REMOVE, false);
		fContainersList.enableButton(IDX_EDIT, false);

		fContainersList.setViewerSorter(new CPElementSorter());

	}

	public void init(ICProject jproject) {
		fCurrJProject = jproject;
		updateLibrariesList();
	}

	private void updateLibrariesList() {
		List cpelements = fCPathList.getElements();
		List libelements = new ArrayList(cpelements.size());

		int nElements = cpelements.size();
		for (int i = 0; i < nElements; i++) {
			CPElement cpe = (CPElement)cpelements.get(i);
			if (isEntryKind(cpe.getEntryKind())) {
				libelements.add(cpe);
			}
		}
		fContainersList.setElements(libelements);
	}

	// -------- ui creation

	public void createControl(Composite parent) {
		PixelConverter converter = new PixelConverter(parent);

		Composite composite = new Composite(parent, SWT.NONE);

		LayoutUtil.doDefaultLayout(composite, new DialogField[]{fContainersList}, true);
		LayoutUtil.setHorizontalGrabbing(fContainersList.getTreeControl(null));

		int buttonBarWidth = converter.convertWidthInCharsToPixels(24);
		fContainersList.setButtonsMinWidth(buttonBarWidth);

		fContainersList.getTreeViewer().addFilter(new ViewerFilter() {
			
			public boolean select(Viewer viewer, Object parentElement, Object element) {
				if ( element instanceof CPElementGroup) {
					return ((CPElementGroup)element).getChildren().length != 0; 
				}
				return true;
			}
		});

		setControl(composite);
	}

	private class ContainersAdapter implements IDialogFieldListener, ITreeListAdapter {

		private final Object[] EMPTY_ARR = new Object[0];

		// -------- IListAdapter --------
		public void customButtonPressed(TreeListDialogField field, int index) {
			containerPageCustomButtonPressed(field, index);
		}

		public void selectionChanged(TreeListDialogField field) {
			containerPageSelectionChanged(field);
		}

		public void doubleClicked(TreeListDialogField field) {
			containerPageDoubleClicked(field);
		}

		public void keyPressed(TreeListDialogField field, KeyEvent event) {
			containerPageKeyPressed(field, event);
		}

		public Object[] getChildren(TreeListDialogField field, Object element) {
			if (element instanceof CPElement) {
				CPElement cpElem = (CPElement)element;
				if (cpElem.getEntryKind() == IPathEntry.CDT_CONTAINER) {
					return new Object[]{new CPElementGroup(cpElem, IPathEntry.CDT_MACRO),
							new CPElementGroup(cpElem, IPathEntry.CDT_INCLUDE), new CPElementGroup(cpElem, IPathEntry.CDT_LIBRARY)};
				} else {
					return ((CPElement)element).getChildren();
				}
			} else if (element instanceof CPElementGroup) {
				return ((CPElementGroup)element).getChildren();
			}
			return EMPTY_ARR;
		}

		public Object getParent(TreeListDialogField field, Object element) {
			if (element instanceof CPElementAttribute) {
				return ((CPElementAttribute)element).getParent();
			} else if (element instanceof CPElementGroup) {
				return ((CPElementGroup)element).getElement();
			}
			return null;
		}

		public boolean hasChildren(TreeListDialogField field, Object element) {
			return (element instanceof CPElement || element instanceof CPElementGroup);
		}

		// ---------- IDialogFieldListener --------

		public void dialogFieldChanged(DialogField field) {
			containerPageDialogFieldChanged(field);
		}
	}

	private void containerPageCustomButtonPressed(DialogField field, int index) {
		CPElement[] containers = null;
		switch (index) {
			case IDX_ADD :
				/* add container */
				containers = openContainerSelectionDialog(null);
				break;
			case IDX_EDIT :
				/* edit */
				editEntry();
				return;
			case IDX_REMOVE :
				/* remove */
				removeEntry();
				return;
		}
		if (containers != null) {
			int nElementsChosen = containers.length;
			// remove duplicates
			List cplist = fContainersList.getElements();
			List elementsToAdd = new ArrayList(nElementsChosen);

			for (int i = 0; i < nElementsChosen; i++) {
				CPElement curr = containers[i];
				if (!cplist.contains(curr) && !elementsToAdd.contains(curr)) {
					elementsToAdd.add(curr);
					//					curr.setAttribute(CPElement.SOURCEATTACHMENT, BuildPathSupport.guessSourceAttachment(curr));
				}
			}

			fContainersList.addElements(elementsToAdd);
			if (index == IDX_ADD) {
				fContainersList.refresh();
			}
			fContainersList.postSetSelection(new StructuredSelection(containers));
		}
	}

	protected void containerPageDoubleClicked(TreeListDialogField field) {
		List selection = fContainersList.getSelectedElements();
		if (canEdit(selection)) {
			editEntry();
		}
	}

	protected void containerPageKeyPressed(TreeListDialogField field, KeyEvent event) {
		if (field == fContainersList) {
			if (event.character == SWT.DEL && event.stateMask == 0) {
				List selection = field.getSelectedElements();
				if (canRemove(selection)) {
					removeEntry();
				}
			}
		}
	}

	private void removeEntry() {
		List selElements = fContainersList.getSelectedElements();
		for (int i = selElements.size() - 1; i >= 0; i--) {
			Object elem = selElements.get(i);
			if (elem instanceof CPElementAttribute) {
				CPElementAttribute attrib = (CPElementAttribute)elem;
				attrib.getParent().setAttribute(attrib.getKey(), null);
				selElements.remove(i);
			}
		}
		if (selElements.isEmpty()) {
			fContainersList.refresh();
			fCPathList.dialogFieldChanged(); // validate
		} else {
			fContainersList.removeElements(selElements);
		}
	}

	private boolean canRemove(List selElements) {
		if (selElements.size() == 0) {
			return false;
		}
		for (int i = 0; i < selElements.size(); i++) {
			Object elem = selElements.get(i);
			if (elem instanceof CPElementAttribute) {
				if ( ((CPElementAttribute)elem).getValue() == null) {
					return false;
				}
			} else if (elem instanceof CPElement) {
				CPElement curr = (CPElement)elem;
				if (curr.getParentContainer() != null) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Method editEntry.
	 */
	private void editEntry() {
		List selElements = fContainersList.getSelectedElements();
		if (selElements.size() != 1) {
			return;
		}
		Object elem = selElements.get(0);
		if (fContainersList.getIndexOfElement(elem) != -1) {
			editElementEntry((CPElement)elem);
		} else if (elem instanceof CPElementAttribute) {
			//			editAttributeEntry((CPElementAttribute) elem);
		}
	}

	//	private void editAttributeEntry(CPElementAttribute elem) {
	//		String key= elem.getKey();
	//		if (key.equals(CPElement.SOURCEATTACHMENT)) {
	//			CPElement selElement= elem.getParent();
	//			
	//			IPath containerPath= null;
	//			boolean applyChanges= false;
	//			Object parentContainer= selElement.getParentContainer();
	//			if (parentContainer instanceof CPElement) {
	//				containerPath= ((CPElement) parentContainer).getPath();
	//				applyChanges= true;
	//			}
	//			SourceAttachmentDialog dialog= new SourceAttachmentDialog(getShell(), selElement.getPathEntry(), containerPath,
	// fCurrJProject, applyChanges);
	//			if (dialog.open() == Window.OK) {
	//				selElement.setAttribute(CPElement.SOURCEATTACHMENT, dialog.getSourceAttachmentPath());
	//				fContainersList.refresh();
	//				fCPathList.refresh(); // images
	//			}
	//		}
	//	}

	private void editElementEntry(CPElement elem) {
		CPElement[] res = null;

		res = openContainerSelectionDialog(elem);
		if (res != null && res.length > 0) {
			CPElement curr = res[0];
			curr.setExported(elem.isExported());
			fContainersList.replaceElement(elem, curr);
		}

	}

	private void containerPageSelectionChanged(DialogField field) {
		List selElements = fContainersList.getSelectedElements();
		fContainersList.enableButton(IDX_EDIT, canEdit(selElements));
		fContainersList.enableButton(IDX_REMOVE, canRemove(selElements));
	}

	private boolean canEdit(List selElements) {
		if (selElements.size() != 1) {
			return false;
		}
		Object elem = selElements.get(0);
		if (elem instanceof CPElement) {
			CPElement curr = (CPElement)elem;
			return ! (curr.getResource() instanceof IFolder) && curr.getParentContainer() == null;
		}
		if (elem instanceof CPElementAttribute) {
			return true;
		}
		return false;
	}

	private void containerPageDialogFieldChanged(DialogField field) {
		if (fCurrJProject != null) {
			// already initialized
			updateCPathList();
		}
	}

	private void updateCPathList() {
		List projelements = fContainersList.getElements();

		List cpelements = fCPathList.getElements();
		int nEntries = cpelements.size();
		// backwards, as entries will be deleted
		int lastRemovePos = nEntries;
		for (int i = nEntries - 1; i >= 0; i--) {
			CPElement cpe = (CPElement)cpelements.get(i);
			int kind = cpe.getEntryKind();
			if (isEntryKind(kind)) {
				if (!projelements.remove(cpe)) {
					cpelements.remove(i);
					lastRemovePos = i;
				}
			}
		}

		cpelements.addAll(lastRemovePos, projelements);

		if (lastRemovePos != nEntries || !projelements.isEmpty()) {
			fCPathList.setElements(cpelements);
		}
	}

	private CPElement[] openContainerSelectionDialog(CPElement existing) {
		IPathEntry elem = null;
		String title;
		if (existing == null) {
			title = CPathEntryMessages.getString("ContainerEntryPage.ContainerDialog.new.title"); //$NON-NLS-1$
		} else {
			title = CPathEntryMessages.getString("ContainerEntryPage.ContainerDialog.edit.title"); //$NON-NLS-1$
			elem = existing.getPathEntry();
		}
		CPathContainerWizard wizard = new CPathContainerWizard(elem, fCurrJProject, getRawClasspath());
		wizard.setWindowTitle(title);
		if (CPathContainerWizard.openWizard(getShell(), wizard) == Window.OK) {
			IPathEntry[] created = wizard.getContainers();
			if (created != null) {
				CPElement[] res = new CPElement[created.length];
				for (int i = 0; i < res.length; i++) {
					res[i] = new CPElement(fCurrJProject, IPathEntry.CDT_CONTAINER, created[i].getPath(), null);
				}
				return res;
			}
		}
		return null;
	}

	private IPathEntry[] getRawClasspath() {
		IPathEntry[] currEntries = new IPathEntry[fCPathList.getSize()];
		for (int i = 0; i < currEntries.length; i++) {
			CPElement curr = (CPElement)fCPathList.getElement(i);
			currEntries[i] = curr.getPathEntry();
		}
		return currEntries;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.jdt.internal.ui.wizards.buildpaths.BuildPathBasePage#isEntryKind(int)
	 */
	public boolean isEntryKind(int kind) {
		return kind == IPathEntry.CDT_CONTAINER;
	}

	/*
	 * @see BuildPathBasePage#getSelection
	 */
	public List getSelection() {
		return fContainersList.getSelectedElements();
	}

	/*
	 * @see BuildPathBasePage#setSelection
	 */
	public void setSelection(List selElements) {
		fContainersList.selectElements(new StructuredSelection(selElements));
	}

	public void performApply(IProgressMonitor monitor) throws CoreException {
		// dinglis-TODO Auto-generated method stub

	}

	public void performDefaults() {
		// dinglis-TODO Auto-generated method stub

	}
}