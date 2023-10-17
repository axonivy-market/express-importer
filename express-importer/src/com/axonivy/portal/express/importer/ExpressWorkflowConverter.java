package com.axonivy.portal.express.importer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ch.ivyteam.ivy.process.model.ProcessKind;
import ch.ivyteam.ivy.process.model.diagram.Diagram;
import ch.ivyteam.ivy.process.model.value.scripting.VariableDesc;
import ch.ivyteam.ivy.process.rdm.IProcess;
import ch.ivyteam.ivy.process.rdm.IProcessManager;
import ch.ivyteam.ivy.process.rdm.IProjectProcessManager;
import ch.ivyteam.ivy.process.rdm.resource.ProcessCreator;
import ch.ivyteam.ivy.resource.datamodel.ResourceDataModelException;
import ch.ivyteam.util.StringUtil;
import ch.ivyteam.util.io.resource.FileResource;

public class ExpressWorkflowConverter {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  static final String NAMESPACE = "express.workflow.";
  private final IProject project;

  public ExpressWorkflowConverter(IProject project) {
    this.project = project;
  }

  public void importJson(String json) throws Exception, ResourceDataModelException {
    JsonNode root = MAPPER.readTree(json);
    JsonNode node = root.get("expressWorkflow");
    String wf = MAPPER.writeValueAsString(node);
    List<ExpressProcess> expressProcessEntities = BusinessEntityConverter
            .jsonValueToEntities(wf, ExpressProcess.class);
    for (ExpressProcess expressProcess : expressProcessEntities) {
      writeProcess(expressProcess);
    }
  }

  private void writeProcess(ExpressProcess expressProcess) throws Exception {
    IProjectProcessManager manager = IProcessManager.instance().getProjectDataModelFor(project);
    IProcess process = manager.findProcessByPath(expressProcess.getProcessName(), false);
    if (process != null) {
      throw new ResourceDataModelException("Process already exists: " + process);
    }
    List<VariableDesc> dataFields = new ArrayList<VariableDesc>();
    String processName = StringUtil.toJavaIdentifier(expressProcess.getProcessName());
    String dataclassName = NAMESPACE + processName + "Data";
    new DialogWriter(project).createDialogs(expressProcess.getTaskDefinitions(), dataFields, dataclassName,
            processName);
    ProcessCreator creator = ProcessCreator.create(project, processName)
            .kind(ProcessKind.NORMAL)
            .namespace("")
            .dataClassName(dataclassName)
            .createDefaultContent(false)
            .dataClassFields(dataFields)
            .toCreator();
    creator.createDataModel();
    process = creator.getCreatedProcess();
    Diagram diagram = process.getModel().getDiagram();
    ProcessWriter writer = new ProcessWriter(project);
    writer.drawElements(
            expressProcess.getTaskDefinitions(),
            diagram,
            processName,
            dataclassName,
            dataFields);
    process.save();
    writer.refreshTree();
  }

  public void from(FileResource file) {
    try {
      importJson(file.read().string(StandardCharsets.UTF_8));
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }
}
