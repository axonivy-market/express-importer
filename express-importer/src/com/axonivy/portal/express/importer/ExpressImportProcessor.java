package com.axonivy.portal.express.importer;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.IStructuredSelection;

import ch.ivyteam.eclipse.util.EclipseUtil;
import ch.ivyteam.eclipse.util.MonitorUtil;
import ch.ivyteam.ivy.designer.ui.wizard.restricted.IWizardSupport;
import ch.ivyteam.ivy.designer.ui.wizard.restricted.WizardStatus;
import ch.ivyteam.ivy.project.IIvyProject;
import ch.ivyteam.ivy.project.IIvyProjectManager;
import ch.ivyteam.util.io.resource.FileResource;
import ch.ivyteam.util.io.resource.nio.NioFileSystemProvider;

public class ExpressImportProcessor implements IWizardSupport, IRunnableWithProgress {

  private IIvyProject selectedSourceProject;
  private FileResource importFile;
  private IStatus status = Status.OK_STATUS;

  public ExpressImportProcessor(IStructuredSelection selection) {
    this.selectedSourceProject = ExpressImportUtil.getFirstNonImmutableIvyProject(selection);
  }

  @Override
  public String getWizardPageTitle(String pageId) {
    return "Import Express Workflow";
  }

  @Override
  public String getWizardPageOkMessage(String pageId) {
    return "Please specify an Express workflow json file";
  }

  @Override
  public boolean wizardFinishInvoked() {
    var okStatus = WizardStatus.createOkStatus();
    okStatus.merge(validateImportFileExits());
    okStatus.merge(validateSource());
    return okStatus.isLowerThan(WizardStatus.ERROR);
  }

  @Override
  public boolean wizardCancelInvoked() {
    return true;
  }

  @Override
  public void run(IProgressMonitor monitor) throws InvocationTargetException {
    SubMonitor progress = MonitorUtil.begin(monitor, "Importing", 1);
    try {
      status = Status.OK_STATUS;
      ResourcesPlugin.getWorkspace().run(m -> {
        new ExpressWorkflowConverter(selectedSourceProject.getProject()).from(importFile);
      }, null, IWorkspace.AVOID_UPDATE, progress);
    } catch (Exception ex) {
      status = EclipseUtil.createErrorStatus(ex);
    } finally {
      MonitorUtil.ensureDone(monitor);
    }
  }

  String getSelectedSourceProjectName() {
    if (selectedSourceProject == null) {
      return StringUtils.EMPTY;
    }
    return selectedSourceProject.getName();
  }

  public WizardStatus setImportFile(String text) {
    if (text != null) {
      importFile = NioFileSystemProvider.create(Path.of("/")).root().file(text);
    } else {
      importFile = null;
    }
    return validateImportFileExits();
  }

  public WizardStatus setSource(String projectName) {
    selectedSourceProject = null;
    if (projectName != null) {
      selectedSourceProject = IIvyProjectManager.instance().getIvyProject(projectName);
    }
    return validateSource();
  }

  public IStatus getStatus() {
    return status;
  }

  private WizardStatus validateImportFileExits() {
    if (importFile == null || !importFile.exists()) {
      return WizardStatus.createErrorStatus("Import file does not exist");
    }
    return WizardStatus.createOkStatus();
  }

  private WizardStatus validateSource() {
    if (selectedSourceProject == null) {
      return WizardStatus.createErrorStatus("Please specify an Axon Ivy project");
    }
    return WizardStatus.createOkStatus();
  }
}
