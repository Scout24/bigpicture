package bigpicture;

import java.awt.Color;
import java.io.File;
import java.io.IOException;

import org.gephi.data.attributes.api.AttributeColumn;
import org.gephi.data.attributes.api.AttributeController;
import org.gephi.data.attributes.api.AttributeModel;
import org.gephi.filters.api.FilterController;
import org.gephi.filters.api.Query;
import org.gephi.filters.api.Range;
import org.gephi.filters.plugin.attribute.AttributeEqualBuilder;
import org.gephi.filters.plugin.attribute.AttributeEqualBuilder.EqualStringFilter;
import org.gephi.filters.plugin.graph.DegreeRangeBuilder.DegreeRangeFilter;
import org.gephi.graph.api.DirectedGraph;
import org.gephi.graph.api.Graph;
import org.gephi.graph.api.GraphController;
import org.gephi.graph.api.GraphModel;
import org.gephi.graph.api.GraphView;
import org.gephi.io.exporter.api.ExportController;
import org.gephi.io.importer.api.Container;
import org.gephi.io.importer.api.EdgeDefault;
import org.gephi.io.importer.api.ImportController;
import org.gephi.io.processor.plugin.DefaultProcessor;
import org.gephi.layout.plugin.force.StepDisplacement;
import org.gephi.layout.plugin.force.yifanHu.YifanHuLayout;
import org.gephi.plugins.layout.noverlap.NoverlapLayout;
import org.gephi.plugins.layout.noverlap.NoverlapLayoutBuilder;
import org.gephi.preview.api.PreviewController;
import org.gephi.preview.api.PreviewModel;
import org.gephi.preview.api.PreviewProperty;
import org.gephi.preview.types.EdgeColor;
import org.gephi.project.api.ProjectController;
import org.gephi.project.api.Workspace;
import org.gephi.ranking.api.Ranking;
import org.gephi.ranking.api.RankingController;
import org.gephi.ranking.api.Transformer;
import org.gephi.ranking.plugin.transformer.AbstractColorTransformer;
import org.gephi.ranking.plugin.transformer.AbstractSizeTransformer;
import org.gephi.statistics.plugin.GraphDistance;
import org.openide.util.Lookup;

import uk.ac.ox.oii.sigmaexporter.SigmaExporter;
import uk.ac.ox.oii.sigmaexporter.model.ConfigFile;

public class LiveModelRenderer {

    public static void main(String[] args) {
        new LiveModelRenderer().run(args[0], args[1]);
    }

    private void run(final String sourceFile, final String targetFilePrefix) {
        //Init a project - and therefore a workspace
        ProjectController pc = Lookup.getDefault().lookup(ProjectController.class);
        pc.newProject();
        Workspace workspace = pc.getCurrentWorkspace();

        //Get models and controllers for this new workspace - will be useful later
        AttributeModel attributeModel = Lookup.getDefault().lookup(AttributeController.class).getModel();
        GraphModel graphModel = Lookup.getDefault().lookup(GraphController.class).getModel();
        PreviewModel model = Lookup.getDefault().lookup(PreviewController.class).getModel();
        ImportController importController = Lookup.getDefault().lookup(ImportController.class);
        FilterController filterController = Lookup.getDefault().lookup(FilterController.class);
        RankingController rankingController = Lookup.getDefault().lookup(RankingController.class);

        //Preview
        model.getProperties().putValue(PreviewProperty.SHOW_NODE_LABELS, Boolean.TRUE);
        model.getProperties().putValue(PreviewProperty.EDGE_COLOR, new EdgeColor(Color.GRAY));
        model.getProperties().putValue(PreviewProperty.EDGE_THICKNESS, new Float(0.1f));
        model.getProperties().putValue(PreviewProperty.NODE_LABEL_FONT, model.getProperties().getFontValue(PreviewProperty.NODE_LABEL_FONT).deriveFont(8));
        model.getProperties().putValue(PreviewProperty.EDGE_CURVED, Boolean.TRUE);

        //Import file
        Container container;
        try {
//            URI uri = getClass().getResource(sourceFile).toURI();
//            File file = new File(uri);
        	File file = new File(System.getProperty("user.dir"), sourceFile);
        	System.out.println("source: " + file);
            container = importController.importFile(file);
            container.getLoader().setEdgeDefault(EdgeDefault.DIRECTED);   //Force DIRECTED
        } catch (Exception ex) {
            ex.printStackTrace();
            return;
        }

        //Append imported data to GraphAPI
        importController.process(container, new DefaultProcessor(), workspace);

        printGraphStats("initial", graphModel);

        //Filter
        AttributeColumn ac = attributeModel.getEdgeTable().getColumn("protocol");
        EqualStringFilter arf = new AttributeEqualBuilder.EqualStringFilter(ac);
        arf.init(graphModel.getGraph());
        arf.setPattern("nfs");
        Query query = filterController.createQuery(arf);
        GraphView view = filterController.filter(query);
        graphModel.setVisibleView(view);    //Set the filter result as the visible view
        printGraphStats("protocol=nfs", graphModel);

        DegreeRangeFilter degreeFilter = new DegreeRangeFilter();
        degreeFilter.init(graphModel.getGraphVisible());
        degreeFilter.setRange(new Range(1, Integer.MAX_VALUE));
        Query query2 = filterController.createQuery(degreeFilter);
        GraphView view2 = filterController.filter(query2);
        graphModel.setVisibleView(view2);
        printGraphStats("degree > 0", graphModel);

        filterController.setSubQuery(query2, query);
        GraphView view3 = filterController.filter(query2);
        graphModel.setVisibleView(view3);    //Set the filter result as the visible view
        printGraphStats("combined", graphModel);
        
        export(targetFilePrefix + ".no-layout.pdf");


        //Run YifanHuLayout for 100 passes - The layout always takes the current visible view
        YifanHuLayout layout = new YifanHuLayout(null, new StepDisplacement(1f));
        layout.setGraphModel(graphModel);
        layout.resetPropertiesValues();
        layout.setOptimalDistance(100f);
        layout.initAlgo();

        for (int i = 0; i < 200 && layout.canAlgo(); i++) {
            layout.goAlgo();
        }
        layout.endAlgo();
        export(targetFilePrefix + ".yifanhu-layout.pdf");

        NoverlapLayoutBuilder nlb = new NoverlapLayoutBuilder();
        NoverlapLayout noverlapLayout = new NoverlapLayout(nlb);
        noverlapLayout.setGraphModel(graphModel);
        noverlapLayout.resetPropertiesValues();
        noverlapLayout.initAlgo();
        for (int i = 0; i < 100 && noverlapLayout.canAlgo(); i++){
        	noverlapLayout.goAlgo();
        }
        noverlapLayout.endAlgo();
        export(targetFilePrefix + ".noverlap-layout.pdf");

        //Get Centrality
        GraphDistance distance = new GraphDistance();
        distance.setDirected(true);
        distance.execute(graphModel, attributeModel);

        //Rank color by Degree
        Ranking degreeRanking = rankingController.getModel().getRanking(Ranking.NODE_ELEMENT, Ranking.DEGREE_RANKING);
        AbstractSizeTransformer sizeTransformer = (AbstractSizeTransformer) rankingController.getModel().getTransformer(Ranking.NODE_ELEMENT, Transformer.RENDERABLE_SIZE);
        sizeTransformer.setMinSize(3);
        sizeTransformer.setMaxSize(40);
        //rankingController.transform(centralityRanking,sizeTransformer);
        rankingController.transform(degreeRanking,sizeTransformer);
        export(targetFilePrefix + ".degree-ranked.pdf");

        //Rank size by centrality
        AttributeColumn centralityColumn = attributeModel.getNodeTable().getColumn(GraphDistance.BETWEENNESS);
        Ranking centralityRanking = rankingController.getModel().getRanking(Ranking.NODE_ELEMENT, centralityColumn.getId());
        AbstractColorTransformer colorTransformer = (AbstractColorTransformer) rankingController.getModel().getTransformer(Ranking.NODE_ELEMENT, Transformer.RENDERABLE_COLOR);
        colorTransformer.setColors(new Color[]{new Color(0xFEF0D9), new Color(0xB30000), Color.blue, Color.green, Color.yellow, Color.orange});
        rankingController.transform(centralityRanking,colorTransformer);

        export(targetFilePrefix + ".color-partitioned.pdf");
        
        SigmaExporter se = new SigmaExporter();
        se.setWorkspace(graphModel.getWorkspace());
    	File outfile = new File(System.getProperty("user.dir"), "out/sigmajs/");
    	
    	ConfigFile cf = new ConfigFile();
    	cf.setDefaults();
    	cf.getFeatures().put("hoverBehavior", "dim");
    	cf.getFeatures().put("groupSelectorAttribute", "loctyp");
    	cf.getSigma().get("drawingProperties").put("defaultEdgeType", "straight");
    	se.setConfigFile(cf, outfile.getAbsolutePath());
        System.out.println(se);
        try {
        	System.out.println(outfile);
        	se.execute();
        	System.out.println("export finished");
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}
        
    }

    private void export(String file) {
	    ExportController ec = Lookup.getDefault().lookup(ExportController.class);
	    try {
	        ec.exportFile(new File(file));
	    } catch (IOException ex) {
	        ex.printStackTrace();
	        return;
	    }
    }
    
    private void printGraphStats(String description, GraphModel graphModel) {
        System.out.println(description);

        Graph graph = graphModel.getGraph();
        DirectedGraph graphVisible = graphModel.getDirectedGraphVisible();

        int vnc = graphVisible.getNodeCount();
        int vec = graphVisible.getEdgeCount();
        int tnc = graph.getNodeCount();
        int tec = graph.getEdgeCount();
        System.out.println("nodes: " + vnc + " / " + tnc + " (" + Math.round(100 * vnc / tnc) + "%)");
        System.out.println("edges: " + vec + " / " + tec + " (" + Math.round(100 * vec / tec) + "%)");
        System.out.println();
    }
}
