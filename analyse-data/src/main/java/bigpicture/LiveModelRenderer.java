package bigpicture;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.apache.pdfbox.exceptions.COSVisitorException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.edit.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.util.PDFMergerUtility;
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
import org.gephi.io.exporter.preview.PDFExporter;
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
import org.gephi.statistics.plugin.Modularity;
import org.openide.util.Lookup;

import uk.ac.ox.oii.sigmaexporter.SigmaExporter;
import uk.ac.ox.oii.sigmaexporter.model.ConfigFile;

import com.itextpdf.text.PageSize;

public class LiveModelRenderer {
    private final List<File> toCombine;

    public LiveModelRenderer() {
    	this.toCombine = new LinkedList<File>();
    }
    
    public static void main(String[] args) {
        new LiveModelRenderer().run(args[0], args[1]);
    }

    private void run(final String sourceFile, final String targetFilePrefix) {
    	renderSubgraph(sourceFile, targetFilePrefix, "nfs");
    	renderSubgraph(sourceFile, targetFilePrefix, "ssh");
        try {
			combineExports(targetFilePrefix + ".pdf");
		} catch (Exception e1) {
			e1.printStackTrace();
		}
    }
    
    private void renderSubgraph(final String sourceFile, final String targetFilePrefix, final String protocol) {
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
        model.getProperties().putValue(PreviewProperty.EDGE_COLOR, new EdgeColor(Color.LIGHT_GRAY));
        model.getProperties().putValue(PreviewProperty.EDGE_THICKNESS, new Float(0.1f));
        model.getProperties().putValue(PreviewProperty.NODE_LABEL_FONT, model.getProperties().getFontValue(PreviewProperty.NODE_LABEL_FONT).deriveFont(8));
        model.getProperties().putValue(PreviewProperty.EDGE_CURVED, Boolean.TRUE);

        //Import file
        Container container;
        try {
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
        export(targetFilePrefix + ".raw.pdf", "raw data");

        //Filter
        AttributeColumn ac = attributeModel.getEdgeTable().getColumn("protocol");
        EqualStringFilter arf = new AttributeEqualBuilder.EqualStringFilter(ac);
        arf.init(graphModel.getGraph());
        arf.setPattern(protocol);
        Query query = filterController.createQuery(arf);
        GraphView view = filterController.filter(query);
        graphModel.setVisibleView(view);    //Set the filter result as the visible view
        printGraphStats("protocol=" + protocol, graphModel);
        export(targetFilePrefix + ".protocol_" + protocol + ".pdf", "edge filter: protocol=" + protocol);

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
        
        export(targetFilePrefix + ".no-layout.pdf", "node filter: degree > 0");

        
        //Run YifanHuLayout for 100 passes - The layout always takes the current visible view
        YifanHuLayout layout = new YifanHuLayout(null, new StepDisplacement(1f));
        layout.setGraphModel(graphModel);
        layout.resetPropertiesValues();
        layout.setOptimalDistance(150f);
        layout.setStepRatio(1f);
        layout.setConverged(true);
        layout.initAlgo();
        for (int i = 0; i < 100 && layout.canAlgo(); i++) {
            layout.goAlgo();
        }
        layout.endAlgo();
        export(targetFilePrefix + ".yifanhu-layout.pdf", "laout: yifanhu");

        //Get Centrality
        GraphDistance distance = new GraphDistance();
        distance.setDirected(true);
        distance.execute(graphModel, attributeModel);

        Modularity m = new Modularity();
        m.setUseWeight(false);
        m.execute(graphModel, attributeModel);
        
        //Rank color by Degree
        Ranking degreeRanking = rankingController.getModel().getRanking(Ranking.NODE_ELEMENT, Ranking.DEGREE_RANKING);
        AbstractSizeTransformer sizeTransformer = (AbstractSizeTransformer) rankingController.getModel().getTransformer(Ranking.NODE_ELEMENT, Transformer.RENDERABLE_SIZE);
        sizeTransformer.setMinSize(12);
        sizeTransformer.setMaxSize(80);
        //rankingController.transform(centralityRanking,sizeTransformer);
        rankingController.transform(degreeRanking,sizeTransformer);
        export(targetFilePrefix + ".degree-ranked.pdf", "ranking: degree as node size");

        //Rank size by centrality
//        AttributeColumn centralityColumn = attributeModel.getNodeTable().getColumn(GraphDistance.BETWEENNESS);
        AttributeColumn centralityColumn = attributeModel.getNodeTable().getColumn(Modularity.MODULARITY_CLASS);
        Ranking centralityRanking = rankingController.getModel().getRanking(Ranking.NODE_ELEMENT, centralityColumn.getId());
        AbstractColorTransformer colorTransformer = (AbstractColorTransformer) rankingController.getModel().getTransformer(Ranking.NODE_ELEMENT, Transformer.RENDERABLE_COLOR);
        
        float[] positions = {0f,0.5f,1f};
        colorTransformer.setColorPositions(positions);
        Color[] colors = new Color[]{new Color(0x0000FF), new Color(0x00FF00),new Color(0xFF0000)};
        colorTransformer.setColors(colors);
//        colorTransformer.setColors(new Color[]{Color.red, Color.gray, Color.blue, Color.green, Color.yellow, Color.orange});
        rankingController.transform(centralityRanking,colorTransformer);

        export(targetFilePrefix + ".color-partitioned.pdf", "partition: modularity as color");

        NoverlapLayoutBuilder nlb = new NoverlapLayoutBuilder();
        NoverlapLayout noverlapLayout = new NoverlapLayout(nlb);
        noverlapLayout.setGraphModel(graphModel);
        noverlapLayout.resetPropertiesValues();
        noverlapLayout.initAlgo();
        for (int i = 0; i < 100 && noverlapLayout.canAlgo(); i++){
        	noverlapLayout.goAlgo();
        }
        noverlapLayout.endAlgo();
        export(targetFilePrefix + ".noverlap-layout.pdf", "layout: reduce overlap");

        export(targetFilePrefix + ".final.pdf", "bigpicture | live state", "protocol: " + protocol);

        renderSigmaJs(protocol, graphModel);
        
    }

	private void renderSigmaJs(final String protocol, GraphModel graphModel) {
		SigmaExporter se = new SigmaExporter();
        se.setWorkspace(graphModel.getWorkspace());
    	File outfile = new File(System.getProperty("user.dir"), "out/sigmajs/" + protocol);
    	
    	ConfigFile cf = new ConfigFile();
    	cf.setDefaults();
    	cf.getFeatures().put("hoverBehavior", "dim");
    	cf.getFeatures().put("groupSelectorAttribute", "date");
    	cf.getSigma().get("drawingProperties").put("defaultEdgeType", "straight");
    	se.setConfigFile(cf, outfile.getAbsolutePath());
        try {
        	se.execute();
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}
	}

    private void export(String filename, String title) {
    	export(filename, title, null);
    }
    
    private void export(String filename, String title, String subtitle) {
    	File file = new File(filename);
    	toCombine.add(file);
    	export2File(file);
    	addTitle(file, title, subtitle);
    }
    
    private void export2File(File file) {
    	ExportController ec = Lookup.getDefault().lookup(ExportController.class);
    	PDFExporter pdfExporter = (PDFExporter) ec.getExporter("pdf");
    	pdfExporter.setLandscape(false);
    	pdfExporter.setPageSize(PageSize.A4);
    	
    	try {
	        ec.exportFile(file, pdfExporter);
	    } catch (IOException ex) {
	        ex.printStackTrace();
	        return;
	    }
    }
    
    private void addTitle(File file, String title, String subtitle) {
		try {
	    	PDDocument doc = PDDocument.load(file);
	
			float fontSize = 24.0f;
			float subtitleFontSize = 16f;
			int offset = 30;
			
			for (Object o: doc.getDocumentCatalog().getAllPages()) {
				PDPage page = (PDPage) o;
				PDRectangle pageSize = page.findMediaBox();
				float topPosition = pageSize.getHeight();
	
				PDPageContentStream contentStream = new PDPageContentStream(doc, page, true, true);
				contentStream.beginText();
				contentStream.setFont(PDType1Font.HELVETICA_BOLD, fontSize);
				contentStream.moveTextPositionByAmount(offset, topPosition - offset - fontSize);
				contentStream.drawString(title);
				if (subtitle != null) {
					contentStream.setFont(PDType1Font.HELVETICA, subtitleFontSize);
					contentStream.moveTextPositionByAmount(0,  -1.5f * subtitleFontSize);
					contentStream.drawString(subtitle);
				}
				contentStream.endText();
				contentStream.close();
			}
			doc.save(file);
		} catch(Exception e) {
			e.printStackTrace();
			return;
		}
    }
    

    private void combineExports(String filename) throws COSVisitorException, IOException {
    	PDFMergerUtility ut = new PDFMergerUtility();
	    for (File file: this.toCombine) {
	    	ut.addSource(file);
	    }
	    ut.setDestinationFileName(filename);
	    ut.mergeDocuments();
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
