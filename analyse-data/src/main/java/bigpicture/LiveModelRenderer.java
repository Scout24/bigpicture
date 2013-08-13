package bigpicture;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
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
import org.gephi.io.exporter.spi.GraphExporter;
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
import org.gephi.preview.api.PreviewProperties;
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
        new LiveModelRenderer().run(args[0], args[1], Arrays.copyOfRange(args, 2, args.length));
    }

    private void run(final String sourceFile, final String filePrefix, String[] protocols) {
        boolean includeSubsteps = false;
        for (String protocol: protocols) {
            renderSubgraph(sourceFile, filePrefix + ".protocol_" + protocol, protocol, includeSubsteps);
        }
        renderSubgraph(sourceFile, filePrefix, null, includeSubsteps);
        try {
            combineExports(filePrefix + ".pdf");
        } catch (Exception e1) {
            e1.printStackTrace();
        }
    }

    private void renderSubgraph(final String sourceFile, final String targetFilePrefix, final String protocol, boolean includeSubsteps) {
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
        PreviewProperties pps = model.getProperties();
        pps.putValue(PreviewProperty.NODE_BORDER_WIDTH, 0);
        //        pps.putValue(PreviewProperty.NODE_OPACITY, .9f);
        pps.putValue(PreviewProperty.SHOW_NODE_LABELS, Boolean.TRUE);
        pps.putValue(PreviewProperty.EDGE_COLOR, new EdgeColor(Color.LIGHT_GRAY));
        pps.putValue(PreviewProperty.EDGE_THICKNESS, new Float(0.1f));
        pps.putValue(PreviewProperty.NODE_LABEL_FONT, model.getProperties().getFontValue(PreviewProperty.NODE_LABEL_FONT).deriveFont(8));
        pps.putValue(PreviewProperty.EDGE_CURVED, Boolean.TRUE);

        //Import file
        Container container;
        try {
            File file = new File(sourceFile);
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
        if (includeSubsteps) {
            export(targetFilePrefix + ".raw.pdf", "raw data");
        }

        if (protocol != null) {
            //Filter
            AttributeColumn ac = attributeModel.getEdgeTable().getColumn("protocol");
            EqualStringFilter arf = new AttributeEqualBuilder.EqualStringFilter(ac);
            arf.init(graphModel.getGraph());
            arf.setPattern(protocol);
            Query query = filterController.createQuery(arf);
            GraphView view = filterController.filter(query);
            graphModel.setVisibleView(view);    //Set the filter result as the visible view
            printGraphStats("protocol=" + protocol, graphModel);
            if (includeSubsteps) {
                export(targetFilePrefix + ".pdf", "edge filter: protocol=" + protocol);
            }
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

            if (includeSubsteps) {
                export(targetFilePrefix + ".no-layout.pdf", "node filter: degree > 0");
            }
        }


        //Run YifanHuLayout for 100 passes - The layout always takes the current visible view
        YifanHuLayout layout = new YifanHuLayout(null, new StepDisplacement(1f));
        layout.setGraphModel(graphModel);
        layout.resetPropertiesValues();
        layout.setOptimalDistance(150f);
        layout.setStepRatio(1f);
        layout.setConverged(true);
        layout.setAdaptiveCooling(true);
        layout.initAlgo();
        for (int i = 0; i < 100 && layout.canAlgo(); i++) {
            layout.goAlgo();
        }
        layout.endAlgo();
        if (includeSubsteps) {
            export(targetFilePrefix + ".yifanhu-layout.pdf", "laout: yifanhu");
        }

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
        sizeTransformer.setMinSize(8);
        sizeTransformer.setMaxSize(20);
        //rankingController.transform(centralityRanking,sizeTransformer);
        rankingController.transform(degreeRanking,sizeTransformer);
        if (includeSubsteps) {
            export(targetFilePrefix + ".degree-ranked.pdf", "ranking: degree as node size");
        }

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

        if (includeSubsteps) {
            export(targetFilePrefix + ".color-partitioned.pdf", "partition: modularity as color");
        }

        NoverlapLayoutBuilder nlb = new NoverlapLayoutBuilder();
        NoverlapLayout noverlapLayout = new NoverlapLayout(nlb);
        noverlapLayout.setGraphModel(graphModel);
        noverlapLayout.resetPropertiesValues();
        noverlapLayout.initAlgo();
        for (int i = 0; i < 100 && noverlapLayout.canAlgo(); i++){
            noverlapLayout.goAlgo();
        }
        noverlapLayout.endAlgo();
        if (includeSubsteps) {
            export(targetFilePrefix + ".noverlap-layout.pdf", "layout: reduce overlap");
        }

        export(targetFilePrefix + ".pdf", "bigpicture | live state", (protocol != null) ? "protocol: " + protocol : null);

        export2gexf(targetFilePrefix + ".gexf");
        export2graphml(targetFilePrefix + ".graphml");

        renderSigmaJs(protocol, graphModel, targetFilePrefix);

    }

    private void renderSigmaJs(final String protocol, GraphModel graphModel, String outdir) {
        SigmaExporter se = new SigmaExporter();
        se.setWorkspace(graphModel.getWorkspace());
        File outfile = new File(outdir);
        outfile.mkdirs();

        ConfigFile cf = new ConfigFile();
        cf.setDefaults();
        cf.getFeatures().put("hoverBehavior", "dim");
        cf.getFeatures().put("groupSelectorAttribute", "protocol");
        cf.getSigma().get("drawingProperties").put("defaultEdgeType", "straight");
        HashMap<String, Object> graphProperties = cf.getSigma().get("graphProperties");
        graphProperties.put("minNodeSize",  "8");
        graphProperties.put("maxNodeSize", "10");
        //    	graphProperties.put("minEdgeSize", .2);
        cf.getInformationPanel().put("groupByEdgeDirection", Boolean.TRUE);
        cf.getText().put("title", "bigpicture | live state - protocol " + protocol);
        se.setConfigFile(cf, outfile.getAbsolutePath());
        System.out.println("outfile: " + outfile.getAbsolutePath());
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

    private void export2gexf(String filename) {
        ExportController ec = Lookup.getDefault().lookup(ExportController.class);
        GraphExporter exporter = (GraphExporter) ec.getExporter("gexf");
        exporter.setExportVisible(true);
        try {
            ec.exportFile(new File(filename), exporter);
        } catch (IOException ex) {
            ex.printStackTrace();
            return;
        }
    }

    private void export2graphml(String filename) {
        ExportController ec = Lookup.getDefault().lookup(ExportController.class);
        GraphExporter exporter = (GraphExporter) ec.getExporter("graphml");
        //exporter.setWorkspace(graphModel.getWorkspace());
        exporter.setExportVisible(true);
        try {
            ec.exportFile(new File(filename), exporter);
        } catch (IOException ex) {
            ex.printStackTrace();
            return;
        }
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
            doc.close();
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
