package ch.ivyteam.ivy.designer.process.ui.internal.express;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.NullProgressMonitor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import ch.ivyteam.ivy.components.ProcessKind;
import ch.ivyteam.ivy.process.IProcess;
import ch.ivyteam.ivy.process.IProcessManager;
import ch.ivyteam.ivy.process.IProjectProcessManager;
import ch.ivyteam.ivy.process.model.diagram.Diagram;
import ch.ivyteam.ivy.process.model.value.scripting.VariableDesc;
import ch.ivyteam.ivy.process.resource.ProcessCreator;
import ch.ivyteam.ivy.resource.datamodel.ResourceDataModelException;
import ch.ivyteam.util.StringUtil;
import ch.ivyteam.util.io.resource.FileResource;


public class ExpressWorkflowConverter
{
  static final int GRID_X = 128;
  static final int GRID_Y = 96;
  static final String NAMESPACE = "axon.ivy.express.";

  private final IProject project;

  public ExpressWorkflowConverter(IProject project) {
    this.project = project;
  }

  public void importJson(String json) throws Exception, ResourceDataModelException
  {
    Gson gson = new GsonBuilder().serializeNulls().create();
    JsonObject jsonObject = gson.fromJson(json, JsonObject.class);

    JsonElement workflowsElement = jsonObject.get("expressWorkflow");
    List<ExpressProcess> expressProcessEntities = BusinessEntityConverter
            .jsonValueToEntities(workflowsElement.toString(), ExpressProcess.class);
    for (ExpressProcess expressProcess : expressProcessEntities)
    {
      writeProcess(expressProcess);
    }
  }

  /*
  private void convert(ExpressProcess expressProcess) throws ResourceDataModelException, IOException
  {

    IProcessModel pm = IApplication.current().findProcessModel(TARGET_PROJECT);
    IProcessModelVersion pmv = pm.getReleasedProcessModelVersion();

    IProject project = pmv.getProject();
    writeProcess(expressProcess, project);
  }
*/

  private void writeProcess(ExpressProcess expressProcess)
          throws ResourceDataModelException, Exception
  {

    IProjectProcessManager manager = IProcessManager.instance().getProjectDataModelFor(project);
    IProcess process = manager.findProcessByPath("Business Processes/" + expressProcess.getProcessName(),
            false);

    if (process != null)
    {
      throw new ResourceDataModelException("Already existing " + process);
    }
    List<VariableDesc> dataFields = new ArrayList<VariableDesc>();
    String processName = StringUtil.toJavaIdentifier(expressProcess.getProcessName());
    String dataclassName = NAMESPACE + processName + "Data";

    new DialogWriter(project).createDialogs(expressProcess.getTaskDefinitions(), dataFields, dataclassName, processName);

    ProcessCreator creator = ProcessCreator.create(project, processName).kind(ProcessKind.NORMAL)
            .namespace("")
            .dataClassName(dataclassName).createDefaultContent(false).dataClassFields(dataFields).toCreator();

    creator.createDataModel(new NullProgressMonitor());
    process = creator.getCreatedProcess();

    Diagram diagram = process.getModel().getDiagram();

    ProcessWriter writer = new ProcessWriter(project);
    writer.drawElements(expressProcess.getTaskDefinitions(), diagram, expressProcess.getProcessName(), dataclassName,
            dataFields, manager);

    process.save();
    writer.refreshTree();
  }

  public void from(FileResource file) {
    Path filePath = Path.of(file.path().toString());
    try {
      importJson(Files.readString(filePath));
    } catch (ResourceDataModelException ex) {
      // TODO Auto-generated catch block
      ex.printStackTrace();
    } catch (Exception ex) {
      // TODO Auto-generated catch block
      ex.printStackTrace();
    }

  }

}