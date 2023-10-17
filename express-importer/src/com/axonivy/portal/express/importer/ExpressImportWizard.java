package com.axonivy.portal.express.importer;

import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.IExportWizard;
import org.eclipse.ui.IWorkbench;

import ch.ivyteam.ivy.designer.ide.DesignerIDEPlugin;

public class ExpressImportWizard extends Wizard implements IExportWizard {

  private ExpressImportProcessor processor;

  public ExpressImportWizard() {
    setWindowTitle("Import");
    var workbenchSettings = DesignerIDEPlugin.getDefault().getDialogSettings();
    var wizardSettings = workbenchSettings.getSection(ExpressImportWizard.class.getName());
    if (wizardSettings == null) {
      wizardSettings = workbenchSettings.addNewSection(ExpressImportWizard.class.getName());
    }
    setDialogSettings(wizardSettings);
  }

  @Override
  public void init(IWorkbench workbench, IStructuredSelection selection) {
    processor = new ExpressImportProcessor(selection);
  }

  @Override
  public void addPages() {
    addPage(new ExpressImportWizardPage(processor));
  }

  @Override
  public boolean performFinish() {
    return ((ExpressImportWizardPage) getPage(ExpressImportWizardPage.PAGE_ID)).finish();
  }

  @Override
  public boolean needsProgressMonitor() {
    return true;
  }
}
