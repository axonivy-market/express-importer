package com.axonivy.portal.express.importer;

import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;

import ch.ivyteam.ivy.process.IProjectProcessManager;
import ch.ivyteam.ivy.process.model.diagram.Diagram;
import ch.ivyteam.ivy.process.model.diagram.shape.DiagramShape;
import ch.ivyteam.ivy.process.model.element.activity.UserTask;
import ch.ivyteam.ivy.process.model.element.activity.value.CallSignatureRef;
import ch.ivyteam.ivy.process.model.element.activity.value.dialog.UserDialogId;
import ch.ivyteam.ivy.process.model.element.activity.value.dialog.UserDialogStart;
import ch.ivyteam.ivy.process.model.element.event.end.TaskEnd;
import ch.ivyteam.ivy.process.model.element.event.start.RequestStart;
import ch.ivyteam.ivy.process.model.element.event.start.value.StartAccessPermissions;
import ch.ivyteam.ivy.process.model.element.gateway.TaskSwitchGateway;
import ch.ivyteam.ivy.process.model.element.value.CaseConfig;
import ch.ivyteam.ivy.process.model.element.value.IvyScriptExpression;
import ch.ivyteam.ivy.process.model.element.value.Mapping;
import ch.ivyteam.ivy.process.model.element.value.Mappings;
import ch.ivyteam.ivy.process.model.element.value.task.Activator;
import ch.ivyteam.ivy.process.model.element.value.task.ActivatorType;
import ch.ivyteam.ivy.process.model.element.value.task.CustomField;
import ch.ivyteam.ivy.process.model.element.value.task.TaskConfig;
import ch.ivyteam.ivy.process.model.element.value.task.TaskConfigs;
import ch.ivyteam.ivy.process.model.element.value.task.TaskIdentifier;
import ch.ivyteam.ivy.process.model.value.MappingCode;
import ch.ivyteam.ivy.process.model.value.scripting.QualifiedType;
import ch.ivyteam.ivy.process.model.value.scripting.VariableDesc;
import ch.ivyteam.ivy.server.restricted.EngineMode;
import ch.ivyteam.util.StringUtil;

class ProcessWriter {

  static final int GRID_X = 128;
  static final int GRID_Y = 96;

  private final IProject project;

  ProcessWriter(IProject project) {
    this.project = project;
  }

  void drawElements(List<ExpressTaskDefinition> tasks, Diagram execDiagram, String processname,
          String dataclassName, List<VariableDesc> dataFields, IProjectProcessManager manager)
  {
    int x = GRID_X;
    int y = GRID_Y;

    DiagramShape start = execDiagram.add().shape(RequestStart.class).at(x, y);
    start.getLabel().setText("start" + processname + ".ivp");
    RequestStart starter = start.getElement();
    makeExecutable(starter, processname, getSteps(tasks));

    DiagramShape previous = start;
    boolean isfirstTask = true;
    for (ExpressTaskDefinition taskdef : tasks)
    {
      x += GRID_X;

      if (taskdef.getResponsibles().size() > 1)
      {
        DiagramShape split = execDiagram.add().shape(TaskSwitchGateway.class).at(x, y);
        split.getLabel().setText("split");
        previous.edges().connectTo(split); // connect
        x += GRID_X;

        DiagramShape current = execDiagram.add().shape(UserTask.class).at(x, y - GRID_Y / 2);
        createUserTask(taskdef, current, dataclassName, isfirstTask, 0);
        isfirstTask = false;
        split.edges().connectTo(current); // connect

        x += GRID_X;
        DiagramShape join = execDiagram.add().shape(TaskSwitchGateway.class).at(x, y);
        join.getLabel().setText("join");
        current.edges().connectTo(join); // connect

        for (int nb = 1; nb < taskdef.getResponsibles().size(); nb++)
        {
          DiagramShape more = execDiagram.add().shape(UserTask.class).at(x - GRID_X,
                  y + nb * GRID_Y - GRID_Y / 2);
          createUserTask(taskdef, more, dataclassName, isfirstTask, nb);
          isfirstTask = false;

          split.edges().connectTo(more); // connect
          more.edges().connectTo(join); // connect

        }
        createSystemTaskGateway(manager, dataFields, split);

        previous = join;

      }
      else
      {
        DiagramShape current = execDiagram.add().shape(UserTask.class).at(x, y);
        createUserTask(taskdef, current, dataclassName, isfirstTask, 0);
        isfirstTask = false;
        previous.edges().connectTo(current); // connect
        if (previous.representsInstanceOf(TaskSwitchGateway.class))
        {
          createSystemTaskGateway(manager, dataFields, previous);
        }

        previous = current;
      }
    }

    x += GRID_X;
    DiagramShape finalreviewtask = execDiagram.add().shape(UserTask.class).at(x, y);
    createFinalReviewTask(finalreviewtask, processname, dataclassName, tasks.size());
    previous.edges().connectTo(finalreviewtask);
    if (previous.representsInstanceOf(TaskSwitchGateway.class))
    {
      createSystemTaskGateway(manager, dataFields, previous);
    }

    x += GRID_X;
    DiagramShape end = execDiagram.add().shape(TaskEnd.class).at(x, y);
    finalreviewtask.edges().connectTo(end);
  }

  private void createSystemTaskGateway(IProjectProcessManager manager, List<VariableDesc> dataFields,
          DiagramShape taskGateway)
  {
    manager.getProcessConfigurator().updateElement(taskGateway.getElement().getLegacyAPI().getZObject());
    TaskSwitchGateway gateway = taskGateway.getElement();
    TaskConfigs taskConfigs = gateway.getTaskConfigs();
    Set<TaskIdentifier> taskIdentifiers = taskConfigs.getTaskIdentifiers();
    for (TaskIdentifier ident : taskIdentifiers)
    {
      TaskConfig taskConfig = taskConfigs.getTaskConfig(ident);
      taskConfig = taskConfig.setName("SYSTEM " + taskGateway.getLabel());
      taskConfig = taskConfig.setActivator(new Activator("SYSTEM", ActivatorType.ROLE));
      taskConfigs = taskConfigs.setTaskConfig(ident, taskConfig);
    }
    gateway.setTaskConfigs(taskConfigs);

    if (gateway.getIncoming().size() > 1) // join
    {
      MappingCode mc = gateway.getOutput();
      Mappings ms = mc.getMappings();
      for (VariableDesc dataField : dataFields)
      {
        String name = dataField.getName();
        String expr = (name.matches(".+[0-9]") ? "in2." + name : "in1." + name);
        ms = ms.add(new Mapping("out." + name, expr));
        mc = mc.setMappings(ms);
      }
      gateway.setOutput(mc);
    }
  }

  private String getSteps(List<ExpressTaskDefinition> tasks)
  {
    StringBuffer sb = new StringBuffer();
    sb.append("\"");
    for (ExpressTaskDefinition task : tasks)
    {
      sb.append(task.getSubject());
      sb.append(",");
    }
    sb.append("Final Review");
    sb.append("\"");
    return sb.toString();
  }

  private void createUserTask(ExpressTaskDefinition taskdef, DiagramShape current,
          String dataclassName,
          boolean isfirstTask, int index)
  {

    current.getLabel().setText(taskdef.getSubject());

    UserTask usertask = current.getElement();
    usertask.setName(taskdef.getSubject());

    TaskConfig taskConfig = usertask.getTaskConfig();
    taskConfig = taskConfig.setName(taskdef.getSubject());
    taskConfig = taskConfig.setDescription(taskdef.getDescription() == null ? "" : taskdef.getDescription());

    taskConfig = taskConfig.setTaskListSkipped(isfirstTask);

    Activator activator = new Activator("\"" + taskdef.getResponsibles().get(index) + "\"",
            ActivatorType.ROLE_FROM_ATTRIBUTE);
    taskConfig = taskConfig.setActivator(activator);

    List<CustomField> customFields = taskConfig.getCustomFields();
    customFields
            .add(new CustomField("stepindex", new IvyScriptExpression("" + (taskdef.getTaskPosition() - 1)),
                    CustomField.Type.NUMBER));
    customFields
            .add(new CustomField("parallelindex", new IvyScriptExpression("" + index),
                    CustomField.Type.NUMBER));

    taskConfig = taskConfig.setCustomFields(customFields);

    taskConfig = taskConfig.setExpiryDelay("new Duration(0,0," + taskdef.getUntilDays() + ",0,0,0)");

    usertask.setTaskConfig(taskConfig);

    createUserTask(usertask, dataclassName);
  }

  private void createFinalReviewTask(DiagramShape finalreviewtask, String processname,
          String dataclassName,
          int index)
  {
    finalreviewtask.getLabel().setText("Final Review");

    UserTask usertask = finalreviewtask.getElement();
    usertask.setName("Final Review");
    usertask.setDescription("Exported AxonIvyExpress Workflow " + processname);

    TaskConfig taskConfig = usertask.getTaskConfig();
    taskConfig = taskConfig.setName(processname + ": Final Review");
    taskConfig.setDescription("The workflow " + processname + " has been finsihed");
    taskConfig = taskConfig.setActivator(new Activator("CREATOR", ActivatorType.ROLE));

    List<CustomField> customFields = taskConfig.getCustomFields();
    customFields
            .add(new CustomField("stepindex", new IvyScriptExpression("" + index), CustomField.Type.NUMBER));
    taskConfig = taskConfig.setCustomFields(customFields);
    usertask.setTaskConfig(taskConfig);

    createUserTask(usertask, dataclassName);

  }

  private void createUserTask(UserTask usertask, String dataclassName) {
    CallSignatureRef signature = new CallSignatureRef("start", List.of(new QualifiedType(dataclassName)));
    UserDialogStart userDialogStart = usertask.getTargetDialog()
        .setId(UserDialogId.create(ExpressWorkflowConverter.NAMESPACE + StringUtil.toJavaIdentifier("TaskDialog")))
        .setStartMethod(signature);
    usertask.setTargetDialog(userDialogStart);
    usertask.setParameters(MappingCode.mapOnly("param.data", "in"));
    usertask.setOutput(MappingCode.mapOnly("out", "result.data"));
  }

  private void makeExecutable(RequestStart starter, String processname, String steps)
  {
    starter.setLinkName("start_" + processname + ".ivp");
    starter.setDescription(processname);
    starter.setStartByHttpRequestAllowed(true);
    starter.setStartName(processname);
    StartAccessPermissions permissions = new StartAccessPermissions("Everybody");
    starter.setRequiredPermissions(permissions);

    CaseConfig caseConfig = starter.getCaseConfig();
    caseConfig = caseConfig.setName(processname);
    List<CustomField> customFields = caseConfig.getCustomFields();
    customFields.add(new CustomField("steps", new IvyScriptExpression(steps), CustomField.Type.STRING));
    customFields.add(
            new CustomField("embedInFrame", new IvyScriptExpression("\"True\""), CustomField.Type.STRING));
    caseConfig = caseConfig.setCustomFields(customFields);
    starter.setCaseConfig(caseConfig);
  }

  void refreshTree()
  {
    try
    {
      if (EngineMode.isEmbeddedInDesigner())
      {
        project.getProject().build(IResource.PROJECT, new NullProgressMonitor());
        project.getFolder("processes").refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
      }
    }
    catch (CoreException e)
    {
      e.printStackTrace();
    }
  }

}