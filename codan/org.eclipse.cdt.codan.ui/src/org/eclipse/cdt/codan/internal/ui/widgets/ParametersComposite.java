/*******************************************************************************
 * Copyright (c) 2009, 2010 Alena Laskavaia 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Alena Laskavaia  - initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.codan.internal.ui.widgets;

import java.io.File;

import org.eclipse.cdt.codan.core.model.CodanSeverity;
import org.eclipse.cdt.codan.core.model.IProblem;
import org.eclipse.cdt.codan.core.model.IProblemWorkingCopy;
import org.eclipse.cdt.codan.core.param.FileScopeProblemPreference;
import org.eclipse.cdt.codan.core.param.IProblemPreference;
import org.eclipse.cdt.codan.core.param.IProblemPreferenceCompositeDescriptor;
import org.eclipse.cdt.codan.core.param.LaunchModeProblemPreference;
import org.eclipse.cdt.codan.core.param.ListProblemPreference;
import org.eclipse.cdt.codan.internal.ui.CodanUIMessages;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.ComboFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.FileFieldEditor;
import org.eclipse.jface.preference.ListEditor;
import org.eclipse.jface.preference.PreferenceStore;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;

/**
 * Composite to show problem preferences
 * 
 */
public class ParametersComposite extends Composite {
	private static final String PREF_ENABLED = "enabled"; //$NON-NLS-1$
	private static final String PREF_SEVERITY = "severity"; //$NON-NLS-1$
	private static final String PREF_MESSAGE = "message"; //$NON-NLS-1$
	private FieldEditorPreferencePage page;
	private IProblem problem;
	private PreferenceStore prefStore;

	/**
	 * @param parent
	 * @param problem
	 * @param style
	 */
	public ParametersComposite(Composite parent, final IProblem problem) {
		super(parent, SWT.NONE);
		if (problem == null)
			throw new NullPointerException();
		this.setLayout(new GridLayout(2, false));
		this.problem = problem;
		this.prefStore = new PreferenceStore();
		page = new FieldEditorPreferencePage(FieldEditorPreferencePage.GRID) {
			@Override
			protected void createFieldEditors() {
				noDefaultAndApplyButton();
				((GridLayout) getFieldEditorParent().getLayout()).numColumns = 2;
				addField(new BooleanFieldEditor(PREF_ENABLED, CodanUIMessages.ParametersComposite_IsEnabled, getFieldEditorParent()));
				String[][] entries = { { CodanSeverity.Error.toString(), CodanSeverity.Error.toString() }, //
						{ CodanSeverity.Warning.toString(), CodanSeverity.Warning.toString() }, //
						{ CodanSeverity.Info.toString(), CodanSeverity.Info.toString() }, //
				};
				addField(new ComboFieldEditor(PREF_SEVERITY, CodanUIMessages.ParametersComposite_Severity, entries, getFieldEditorParent()));
				addField(new StringFieldEditor(PREF_MESSAGE, CodanUIMessages.ParametersComposite_MessagePattern, getFieldEditorParent()));
				IProblemPreference pref = problem.getPreference();
				createFieldEditorsForParameters(pref);
			}

			@Override
			protected Control createContents(Composite parent) {
				return super.createContents(parent);
			}

			/**
			 * @param info
			 */
			private void createFieldEditorsForParameters(final IProblemPreference info) {
				if (info == null)
					return;
				if (info.getKey() == FileScopeProblemPreference.KEY)
					return; // skip the scope
				if (info.getKey() == LaunchModeProblemPreference.KEY)
					return; // skip the launch
				switch (info.getType()) {
					case TYPE_STRING: {
						StringFieldEditor fe = new StringFieldEditor(info.getQualifiedKey(), info.getLabel(), getFieldEditorParent());
						addField(fe);
						break;
					}
					case TYPE_BOOLEAN: {
						BooleanFieldEditor fe = new BooleanFieldEditor(info.getQualifiedKey(), info.getLabel(), getFieldEditorParent());
						addField(fe);
						break;
					}
					case TYPE_LIST:
						ListEditor le = new ListEditor(info.getQualifiedKey(), info.getLabel(), getFieldEditorParent()) {
							@Override
							protected String[] parseString(String stringList) {
								ListProblemPreference list = (ListProblemPreference) info;
								IProblemPreference[] childDescriptors = list.getChildDescriptors();
								if (childDescriptors.length == 0)
									return new String[0];
								String res[] = new String[childDescriptors.length];
								for (int i = 0; i < childDescriptors.length; i++) {
									IProblemPreference item = childDescriptors[i];
									res[i] = String.valueOf(item.getValue());
								}
								return res;
							}

							@Override
							protected String getNewInputObject() {
								ListProblemPreference list = (ListProblemPreference) info;
								String label = list.getChildDescriptor().getLabel();
								InputDialog dialog = new InputDialog(getShell(), CodanUIMessages.ParametersComposite_NewValue, label,
										"", null); //$NON-NLS-1$
								if (dialog.open() == Window.OK) {
									return dialog.getValue();
								}
								return null;
							}

							@Override
							protected String createList(String[] items) {
								ListProblemPreference list = (ListProblemPreference) info.clone();
								list.clear();
								for (int i = 0; i < items.length; i++) {
									String val = items[i];
									list.addChildValue(val);
								}
								return list.exportValue();
							}
						};
						addField(le);
						break;
					case TYPE_MAP:
						IProblemPreference[] childrenDescriptor = ((IProblemPreferenceCompositeDescriptor) info).getChildDescriptors();
						for (int i = 0; i < childrenDescriptor.length; i++) {
							IProblemPreference desc = childrenDescriptor[i];
							createFieldEditorsForParameters(desc);
						}
						break;
					case TYPE_CUSTOM: {
						StringFieldEditor fe = new StringFieldEditor(info.getQualifiedKey(), info.getLabel(), getFieldEditorParent());
						addField(fe);
						break;
					}
					case TYPE_FILE: {
						FileFieldEditor fe = new FileFieldEditor(info.getQualifiedKey(), info.getLabel(), getFieldEditorParent());
						addField(fe);
						break;
					}
					default:
						throw new UnsupportedOperationException(info.getType().toString());
				}
			}
		};
		load(problem);
		page.setPreferenceStore(prefStore);
		page.createControl(parent);
		page.getControl().setLayoutData(new GridData(GridData.FILL_BOTH));
	}

	/**
	 * @param problem
	 */
	public void load(final IProblem problem) {
		IProblemPreference info = problem.getPreference();
		if (info == null) {
			Label label = new Label(this, 0);
			label.setText(CodanUIMessages.ParametersComposite_None);
		} else {
			initPrefStore(info);
		}
		prefStore.setValue(PREF_ENABLED, problem.isEnabled());
		prefStore.setValue(PREF_SEVERITY, problem.getSeverity().toString());
		prefStore.setValue(PREF_MESSAGE, problem.getMessagePattern());
	}

	public void save(IProblemWorkingCopy problem) {
		page.performOk();
		savePrefStore(problem.getPreference());
		problem.setEnabled(prefStore.getBoolean(PREF_ENABLED));
		problem.setSeverity(CodanSeverity.valueOf(prefStore.getString(PREF_SEVERITY)));
		problem.setMessagePattern(prefStore.getString(PREF_MESSAGE));
	}

	private void savePrefStore(IProblemPreference desc) {
		if (desc == null)
			return;
		String key = desc.getQualifiedKey();
		switch (desc.getType()) {
			case TYPE_STRING:
				desc.setValue(prefStore.getString(key));
				break;
			case TYPE_BOOLEAN:
				desc.setValue(prefStore.getBoolean(key));
				break;
			case TYPE_INTEGER:
				desc.setValue(prefStore.getInt(key));
				break;
			case TYPE_FILE:
				desc.setValue(new File(prefStore.getString(key)));
				break;
			case TYPE_LIST:
				desc.importValue(prefStore.getString(key));
				break;
			case TYPE_CUSTOM:
				desc.importValue(prefStore.getString(key));
				break;
			case TYPE_MAP:
				IProblemPreference[] childrenDescriptor = ((IProblemPreferenceCompositeDescriptor) desc).getChildDescriptors();
				for (int i = 0; i < childrenDescriptor.length; i++) {
					IProblemPreference chi = childrenDescriptor[i];
					savePrefStore(chi);
				}
				break;
			default:
				throw new UnsupportedOperationException(desc.getType().toString());
		}
	}

	private void initPrefStore(IProblemPreference desc) {
		if (desc == null || desc.getValue() == null)
			return;
		String key = desc.getQualifiedKey();
		switch (desc.getType()) {
			case TYPE_STRING:
				prefStore.setValue(key, (String) desc.getValue());
				break;
			case TYPE_BOOLEAN:
				prefStore.setValue(key, (Boolean) desc.getValue());
				break;
			case TYPE_INTEGER:
				prefStore.setValue(key, (Integer) desc.getValue());
				break;
			case TYPE_FILE:
				prefStore.setValue(key, ((File) desc.getValue()).getPath());
				break;
			case TYPE_LIST:
				prefStore.setValue(key, desc.exportValue());
				break;
			case TYPE_CUSTOM:
				prefStore.setValue(key, desc.exportValue());
				break;
			case TYPE_MAP:
				IProblemPreference[] childrenDescriptor = ((IProblemPreferenceCompositeDescriptor) desc).getChildDescriptors();
				for (int i = 0; i < childrenDescriptor.length; i++) {
					IProblemPreference chi = childrenDescriptor[i];
					initPrefStore(chi);
				}
				break;
			default:
				throw new UnsupportedOperationException(desc.getType().toString());
		}
	}

	/**
	 * @return the problem
	 */
	public IProblem getProblem() {
		return problem;
	}
}
