package com.axonivy.portal.express.importer;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.IProject;

import ch.ivyteam.ivy.dialog.configuration.DialogCreationParameters;
import ch.ivyteam.ivy.dialog.configuration.IUserDialog;
import ch.ivyteam.ivy.dialog.configuration.IUserDialogManager;
import ch.ivyteam.ivy.dialog.configuration.jsf.JsfViewTechnologyConfiguration;
import ch.ivyteam.ivy.dialog.ui.IViewTechnologyDesignerUi;
import ch.ivyteam.ivy.dialog.ui.ViewTechnologyDesignerUiRegistry;
import ch.ivyteam.ivy.process.model.diagram.Diagram;
import ch.ivyteam.ivy.process.model.diagram.shape.DiagramShape;
import ch.ivyteam.ivy.process.model.element.event.end.dialog.html.HtmlDialogEnd;
import ch.ivyteam.ivy.process.model.element.event.start.dialog.html.HtmlDialogMethodStart;
import ch.ivyteam.ivy.process.model.element.event.start.value.CallSignature;
import ch.ivyteam.ivy.process.model.element.value.Mapping;
import ch.ivyteam.ivy.process.model.value.MappingCode;
import ch.ivyteam.ivy.process.model.value.scripting.VariableDesc;
import ch.ivyteam.ivy.process.rdm.IProcess;
import ch.ivyteam.ivy.project.IvyProjectNavigationUtil;
import ch.ivyteam.ivy.resource.datamodel.ResourceDataModelException;
import ch.ivyteam.util.StringUtil;

class DialogWriter {

  private final IProject project;

  public DialogWriter(IProject project) {
    this.project = project;
  }

  void createDialogs(List<ExpressTaskDefinition> tasks,
          List<VariableDesc> dataFields, String dataclassName, String processName)
          throws ResourceDataModelException, Exception
  {
    for (ExpressTaskDefinition taskdef : tasks)
    {
      createDialogComponent(taskdef, dataFields);
    }
    createDialogMaster(tasks,dataclassName, processName);
  }

  private void createDialogMaster(List<ExpressTaskDefinition> tasks,
          String dataclassName, String processName)
          throws Exception
  {
    StringBuffer stepsPanel = new StringBuffer();
    StringBuffer formPanel = new StringBuffer();

    writeDialogMasterStepPanel(tasks, stepsPanel);
    writeDialogMasterFormPanel(tasks, formPanel);

    InputStream is = ExpressWorkflowConverter.class.getResourceAsStream("task_template.xhtml");
    String template = new String(is.readAllBytes());
    template = template.replace("${pagetitle}", processName);
    template = template.replace("${stepspanel}", stepsPanel.toString());
    template = template.replace("${formpanel}", formPanel.toString());
    is.close();

    List<VariableDesc> inputParameters = Arrays.asList(new VariableDesc("data", dataclassName));
    List<VariableDesc> outputParameters = Arrays.asList(new VariableDesc("data", dataclassName));
    CallSignature dlgCallSigature = new CallSignature("start", inputParameters, outputParameters);

    List<VariableDesc> dlgDataFields = Arrays.asList(new VariableDesc("processData", dataclassName),
            new VariableDesc("currentStep", "java.lang.Integer"),
            new VariableDesc("parallelIndex", "java.lang.Integer"));
    List<Mapping> paramMappings = Arrays.asList(new Mapping("out.processData", "param.data"),
            new Mapping("out.currentStep",
                    "ivy.task.customFields().numberField(\"stepindex\").getOrDefault(0)"),
            new Mapping("out.parallelIndex",
                    "ivy.task.customFields().numberField(\"parallelindex\").getOrDefault(0)"));
    List<Mapping> resultMappings = Arrays.asList(new Mapping("result.data", "in.processData"));

    var ivyProject =IvyProjectNavigationUtil.getIvyProject(project);
    IViewTechnologyDesignerUi viewTech = ViewTechnologyDesignerUiRegistry.getInstance().getViewTechnology(JsfViewTechnologyConfiguration.TECHNOLOGY_IDENTIFIER);
    viewTech.getViewLayoutProvider().getViewLayouts(ivyProject).get(0).getViewContent(ExpressWorkflowConverter.NAMESPACE, "frame-10", null);

    DialogCreationParameters params = new DialogCreationParameters.Builder(project,
            ExpressWorkflowConverter.NAMESPACE + StringUtil.toJavaIdentifier("TaskDialog")).viewContent(template)
                    .dataClassFields(dlgDataFields).calleeParamMappings(paramMappings)
                    .calleeResultMappings(resultMappings)
                    .signature(dlgCallSigature)
                    .addCreationParameter(template, resultMappings).toCreationParams();
    IUserDialogManager.instance().getProjectDataModelFor(project)
            .createProjectUserDialog(params);

  }

  private void createFileUploadEventHandler(IUserDialog dialog)
  {
    IProcess process = dialog.getProcess();
    Diagram diagram = process.getModel().getDiagram();
    DiagramShape start = diagram.add().shape(HtmlDialogMethodStart.class).at(96, 260);
    DiagramShape end = diagram.add().shape(HtmlDialogEnd.class).at(224, 260);
    start.edges().connectTo(end);
    HtmlDialogMethodStart startmethod = start.getElement();
    startmethod.setName("handleFileUpload");
    List<VariableDesc> methodInputParams = Arrays
            .asList(new VariableDesc("event", "org.primefaces.event.FileUploadEvent"));
    startmethod.setSignature(new CallSignature("handleFileUpload", methodInputParams, Arrays.asList()));

    MappingCode mc = startmethod.getParameterMappings();
    mc = mc.setCode("org.primefaces.model.file.UploadedFile f = param.event.getFile();\n"
            + "File ivyFile = new File(f.getFileName(), true);\n"
            + "ivyFile.writeBinary(f.getContent());\n"
            + "ivy.case.documents().add(ivyFile.getPath()).write().withContentFrom(ivyFile);");
    startmethod.setParameterMappings(mc);

    process.save();
  }

  private void writeDialogMasterFormPanel(List<ExpressTaskDefinition> tasks, StringBuffer formPanel)
  {
    for (int t = 0; t < tasks.size(); t++)
    {
      ExpressTaskDefinition taskdef = tasks.get(t);
      String componentName = StringUtil.toJavaIdentifier(taskdef.getSubject());
      String componentData = "data.processData." + componentName.toLowerCase();

      formPanel.append(
              "<p:fieldset legend=\"Form\" toggleable=\"true\" collapsed=\"false\" rendered=\"#{data.currentStep == "
                      + t
                      + " and data.parallelIndex == 0}\">\n");
      formPanel.append("<b>"+taskdef.getDescription()+"</b><hr/>\n");
      formPanel.append("<ic:" + ExpressWorkflowConverter.NAMESPACE + componentName + " data=\"#{" + componentData
              + "}\" editable=\"true\"/>\n");
      formPanel.append("</p:fieldset>\n");

      for (int parallelInstance = 1; parallelInstance < taskdef.getResponsibles().size(); parallelInstance++)
      {
        componentData = "data.processData." + componentName.toLowerCase() + (parallelInstance + 1);
        formPanel.append(
                "<p:fieldset legend=\"Form\" toggleable=\"true\" collapsed=\"false\" rendered=\"#{data.currentStep == "
                        + t
                        + " and data.parallelIndex == " + parallelInstance + "}\">\n");
        formPanel.append("<b>"+taskdef.getDescription()+"</b><hr/>\n");
        formPanel.append("<ic:" + ExpressWorkflowConverter.NAMESPACE + componentName + " data=\"#{" + componentData
                + "}\" editable=\"true\"/>\n");
        formPanel.append("</p:fieldset>\n");
      }
    }
  }

  private void writeDialogMasterStepPanel(List<ExpressTaskDefinition> tasks, StringBuffer stepsPanel)
  {
    for (int stepIndex = 0; stepIndex < tasks.size(); stepIndex++)
    {
      ExpressTaskDefinition taskdef = tasks.get(stepIndex);

      String stepName = taskdef.getSubject();
      String componentName = StringUtil.toJavaIdentifier(taskdef.getSubject());
      String componentData = "data.processData." + componentName.toLowerCase();
      String responsableName = "<b>Applikant:</b> #{" + componentData + ".wfuser}";

      stepsPanel.append(
              "<p:fieldset styleClass='finished-fieldset' toggleable='true' rendered='#{data.currentStep gt "
                      + stepIndex + "}' collapsed='" + (stepIndex==0 ? "false" : "true")+"' legend='" + stepName + "'>\n");
      stepsPanel.append(responsableName + "<hr/>\n");
      stepsPanel
              .append("<b>Form Details:</b><ic:" + ExpressWorkflowConverter.NAMESPACE + componentName + " data=\"#{" + componentData
                      + "}\" editable=\"false\"/>\n");
      stepsPanel.append("</p:fieldset>\n");

      for (int parallelInstance = 1; parallelInstance < taskdef.getResponsibles().size(); parallelInstance++)
      {
        componentData = "data.processData." + componentName.toLowerCase() + (parallelInstance + 1);
        responsableName = "<b>Applikant</b><br/>Full name: #{" + componentData + ".wfuser}";
        stepsPanel.append(
                "<p:fieldset styleClass='finished-fieldset' toggleable='true' rendered='#{data.currentStep gt "
                        + stepIndex + "}' collapsed='true' legend='" + stepName + "'>\n");
        stepsPanel.append("<p:panel styleClass='card'>" + responsableName + "<hr/>\n");
        stepsPanel.append(
                "<b>Form Details:</b><ic:" + ExpressWorkflowConverter.NAMESPACE + componentName + " data=\"#{" + componentData
                        + "}\" editable=\"false\"/>\n");
        stepsPanel.append("</p:panel></p:fieldset>\n");
      }
    }
  }

  private void createDialogComponent(ExpressTaskDefinition taskdef,
          List<VariableDesc> dataFields) throws ResourceDataModelException, IOException
  {
    List<ExpressFormElement> formElements = taskdef.getFormElements();
    var form = parseFormElements(formElements);

    var component = new FormComponent(StringUtil.toJavaIdentifier(taskdef.getSubject()));
    dataFields.add(new VariableDesc(component.name.toLowerCase(), component.dataClass));
    for (int parallelInstance = 1; parallelInstance < taskdef.getResponsibles().size(); parallelInstance++)
    {
      var variable = new VariableDesc(component.name.toLowerCase() + (parallelInstance + 1), component.dataClass);
      dataFields.add(variable);
    }

    var panels = form.panels;
    String viewForm = buildTaskForm(taskdef, panels);

    List<VariableDesc> inputParameters = Arrays.asList(new VariableDesc("data", component.dataClass));
    List<VariableDesc> outputParameters = Arrays.asList(new VariableDesc("data", component.dataClass));
    CallSignature dlgCallSigature = new CallSignature("start", inputParameters, outputParameters);

    List<Mapping> paramMappings = Arrays.asList(new Mapping("out", "param.data"));
    List<Mapping> resultMappings = Arrays.asList(new Mapping("result.data", "in"),
            new Mapping("result.data.wfuser",
                    "in.wfuser.isEmpty() ? ivy.session.getSessionUserName() : in.wfuser"));

    DialogCreationParameters params = new DialogCreationParameters.Builder(project, component.qualifiedName)
            .viewContent(viewForm).dataClassFields(form.dialogDataFields).calleeParamMappings(paramMappings)
            .calleeResultMappings(resultMappings).signature(dlgCallSigature).toCreationParams();
    IUserDialog dialog = IUserDialogManager.instance().getProjectDataModelFor(project)
            .createProjectUserDialog(params);

    if (form.withFileUpload)
    {
      createFileUploadEventHandler(dialog);
    }
  }

  private String buildTaskForm(ExpressTaskDefinition taskdef, Panels panels) throws IOException
  {
    String template = "";
    try (InputStream is = ExpressWorkflowConverter.class
            .getResourceAsStream("component_template.xhtml"))
    {
      template = new String(is.readAllBytes())
       .replace("${headerpanelfields}", panels.header.toString())
       .replace("${leftpanelfields}", panels.left.toString())
       .replace("${rightpanelfields}", panels.right.toString())
       .replace("${footerpanelfields}", panels.footer.toString())
       .replace("${tasktitle}", taskdef.getSubject())
       .replace("${taskdescription}", "" + taskdef.getDescription());
    }
    return template;
  }

  private FormParseResult parseFormElements(List<ExpressFormElement> formElements) throws IOException
  {
    var result = new FormParseResult();
    result.dialogDataFields.add(new VariableDesc("wfuser", "java.lang.String"));
    if (formElements == null) {
      return result;
    }
    for (ExpressFormElement formElement : formElements)
    {
      String datafield = StringUtil.toJavaIdentifier(formElement.getLabel().replace(":", ""));

      if (formElement.getElementType().equals("ManyCheckbox"))
      {
        result.dialogDataFields
                .add(new VariableDesc(datafield, "ch.ivyteam.ivy.scripting.objects.List<java.lang.String>"));
      }
      else
      {
        result.dialogDataFields.add(new VariableDesc(datafield, "java.lang.String"));
      }
      result.withFileUpload = result.withFileUpload || formElement.getElementType().equals("FileUpload");

      switch (formElement.getElementPosition())
      {
        case "HEADER":
          writeFormElement(result.panels.header, formElement, datafield);
          break;
        case "LEFTPANEL":
          writeFormElement(result.panels.left, formElement, datafield);
          break;
        case "RIGHTPANEL":
          writeFormElement(result.panels.right, formElement, datafield);
          break;
        case "FOOTER":
          writeFormElement(result.panels.footer, formElement, datafield);
      }
    }
    return result;
  }

  private void writeFormElement(StringBuffer sb, ExpressFormElement formElement, String datafield)
          throws IOException
  {
    if (sb.length() > 1)
    {
      sb.append("<br/><br/>");
    }
    sb.append("<p:outputLabel value='" + formElement.getLabel() + "' for='"+datafield+"'/>\n");

    switch (formElement.getElementType())
    {
      case "InputFieldText":
        sb.append("<p:inputText id='"+datafield+"' value='#{data." + datafield + "}' required='"+formElement.isRequired()+"'/>\n");
        break;
      case "InputTextArea":
        sb.append("<p:inputTextarea id='"+datafield+"'  value='#{data." + datafield + "}' required='"+formElement.isRequired()+"'/>\n");
        break;
      case "ManyCheckbox":
        sb.append("<p:selectManyCheckbox id='"+datafield+"' value='#{data." + datafield + "}' layout='grid' columns='1'>\n");
        List<String> opts = formElement.getOptionStrs();
        for (String option : opts)
        {
          sb.append("<f:selectItem itemLabel='" + option + "' itemValue='" + option + "' />\n");
        }
        sb.append("</p:selectManyCheckbox>");
        break;
      case "OneRadio":
        sb.append("<p:selectOneRadio id='"+datafield+"' value='#{data." + datafield + "}' layout='grid' columns='1'>\n");
        List<String> options = formElement.getOptionStrs();
        for (String option : options)
        {
          sb.append("<f:selectItem itemLabel='" + option + "' itemValue='" + option + "' />\n");
        }
        sb.append("</p:selectOneRadio>");
        break;
      case "FileUpload":
        try(InputStream is = ExpressWorkflowConverter.class.getResourceAsStream("fileupload_template.xhtml")){
          String fileuploadTemplate = new String(is.readAllBytes())
             .replace("${fieldname}", datafield);
          sb.append(fileuploadTemplate);
        }
    }

  }

  private class Panels {
    StringBuffer header = new StringBuffer();
    StringBuffer left = new StringBuffer();
    StringBuffer right = new StringBuffer();
    StringBuffer footer = new StringBuffer();
  }

  private class FormParseResult {
    List<VariableDesc> dialogDataFields = new ArrayList<VariableDesc>();
    boolean withFileUpload = false;
    Panels panels = new Panels();
  }

  private class FormComponent {

    private String name;
    private String qualifiedName;
    private String dataClass;

    FormComponent(String componentName) {
      this.name = componentName;
      this.qualifiedName = ExpressWorkflowConverter.NAMESPACE + componentName;
      this.dataClass = qualifiedName + "." + componentName + "Data";
    }

  }
}