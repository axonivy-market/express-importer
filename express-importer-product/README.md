# Axon Ivy Express Importer

Imports your no-code Express Workflow from the Portal into a Designer Workflow Application.

## Demo

1. Login to the Portal
2. Export and download a low-code express Process of your choice as JSON. See [Portal docs](https://market.axonivy.com/portal/10.0.1.1/doc/portal-user-guide/axon-ivy-express/index.html#howto-export-an-express-process) on Express exporting.
3. Open your Designer, got to `File` > `Import...` > `Axon Ivy` > `Ivy Express Workflow` ![wizard](img/express-import-wiz.png)
4. Select the target project for the import. And use the previously download express workflow .json as source. ![sources](img/select-source-and-target.png)
5. Finish the Wizard
6. Run your and extend your imported Process ![wizard](img/run-imported-process.png)


## Setup

1. Copy the express-import.jar into the `dropins` directory of your Axon Ivy Designer
2. Restart your Axon Ivy Designer