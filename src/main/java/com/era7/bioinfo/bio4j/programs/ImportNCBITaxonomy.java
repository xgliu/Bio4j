/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.era7.bioinfo.bio4j.programs;

import com.era7.bioinfo.bio4j.model.nodes.OrganismNode;
import com.era7.bioinfo.bio4j.model.nodes.TaxonNode;
import com.era7.bioinfo.bio4j.model.nodes.ncbi.NCBITaxonNode;
import com.era7.bioinfo.bio4j.model.relationships.ncbi.NCBIMainTaxonRel;
import com.era7.bioinfo.bio4j.model.relationships.ncbi.NCBITaxonParentRel;
import com.era7.bioinfo.bio4j.model.relationships.ncbi.NCBITaxonRel;
import com.era7.bioinfo.bio4j.model.util.Bio4jManager;
import com.era7.bioinfo.bio4j.model.util.NodeRetriever;
import com.era7.lib.bioinfo.bioinfoutil.Executable;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

/**
 * Imports NCBI taxonomy into Bio4j
 *
 * @author Pablo Pareja Tobes <ppareja@era7.com>
 */
public class ImportNCBITaxonomy implements Executable {

    private static final Logger logger = Logger.getLogger("ImportNCBITaxonomy");
    private static FileHandler fh;

    @Override
    public void execute(ArrayList<String> array) {
        String[] args = new String[array.size()];
        for (int i = 0; i < array.size(); i++) {
            args[i] = array.get(i);
        }
        main(args);
    }

    public static void main(String[] args) {

        if (args.length != 5) {
            System.out.println("This program expects the following parameters: \n"
                    + "1. Nodes DMP filename \n"
                    + "2. Names DMP filename \n"
                    + "3. Merged DMP filename \n"
                    + "4. Bio4j DB folder \n"
                    + "5. Associate Uniprot taxonomy (true/false)");
        } else {

            Bio4jManager manager = null;
            Transaction txn = null;
            int txnCounter = 0;
            int txnLimitForCommit = 10000;

            boolean associateUniprotTaxonomy = Boolean.parseBoolean(args[4]);

            try {

                // This block configure the logger with handler and formatter
                fh = new FileHandler("ImportNCBITaxonomy.log", true);
                SimpleFormatter formatter = new SimpleFormatter();
                fh.setFormatter(formatter);
                logger.addHandler(fh);
                logger.setLevel(Level.ALL);

                File nodesDumpFile = new File(args[0]);
                File namesDumpFile = new File(args[1]);
                File mergedDumpFile = new File(args[2]);

                BufferedReader reader = new BufferedReader(new FileReader(nodesDumpFile));
                String line = null;

                logger.log(Level.INFO, "creating manager...");
                manager = new Bio4jManager(args[3], true, false);
                NodeRetriever nodeRetriever = new NodeRetriever(manager);


                HashMap<String, String> nodeParentMap = new HashMap<String, String>();

                txn = manager.beginTransaction();

                logger.log(Level.INFO, "reading nodes file...");

                while ((line = reader.readLine()) != null) {

                    if (line.trim().length() > 0) {

                        String[] columns = line.split("\\|");

                        NCBITaxonNode node = new NCBITaxonNode(manager.createNode());
                        //setting node_type property
                        node.setNodeType(NCBITaxonNode.NODE_TYPE);

                        node.setTaxId(columns[0].trim());
                        node.setRank(columns[2].trim());
                        node.setEmblCode(columns[3].trim());

                        //indexing the node..
                        manager.getNCBITaxonIdIndex().add(node.getNode(), NCBITaxonNode.NCBI_TAXON_ID_INDEX, node.getTaxId());

                        //indexing the node by its node_type
                        manager.getNodeTypeIndex().add(node.getNode(), Bio4jManager.NODE_TYPE_INDEX_NAME, NCBITaxonNode.NODE_TYPE);

                        //saving the parent of the node for later
                        nodeParentMap.put(node.getTaxId(), columns[1].trim());

                        txnCounter++;

                        if (txnCounter % txnLimitForCommit == 0) {
                            txn.success();
                            txn.finish();
                            txn = manager.beginTransaction();
                        }

                    }

                }

                //commiting and 'restarting' transaction
                txn.success();
                txn.finish();
                txn = manager.beginTransaction();
                txnCounter = 0;

                reader.close();
                logger.log(Level.INFO, "done!");

                logger.log(Level.INFO, "reading names file...");
                //------------reading names file-----------------
                reader = new BufferedReader(new FileReader(namesDumpFile));
                while ((line = reader.readLine()) != null) {

                    String[] columns = line.split("\\|");

                    if (columns[columns.length - 1].trim().equals("scientific name")) {

                        String taxId = columns[0].trim();
                        String nameSt = columns[1].trim();

                        NCBITaxonNode node = nodeRetriever.getNCBITaxonByTaxId(taxId);
                        node.setScientificName(nameSt);

                        txnCounter++;
                        if (txnCounter % txnLimitForCommit == 0) {
                            //commiting and 'restarting' transaction
                            txn.success();
                            txn.finish();
                            txn = manager.beginTransaction();
                        }
                    }

                }
                reader.close();
                logger.log(Level.INFO, "done!");

                logger.log(Level.INFO, "storing relationships...");

                //commiting and 'restarting' transaction
                txn.success();
                txn.finish();
                txn = manager.beginTransaction();
                txnCounter = 0;

                Set<String> nodesSet = nodeParentMap.keySet();
                for (String nodeTaxId : nodesSet) {

                    String parentTaxId = nodeParentMap.get(nodeTaxId);

                    NCBITaxonNode currentNode = nodeRetriever.getNCBITaxonByTaxId(nodeTaxId);

                    if (!nodeTaxId.equals(parentTaxId)) {

                        NCBITaxonNode parentNode = nodeRetriever.getNCBITaxonByTaxId(parentTaxId);
                        parentNode.getNode().createRelationshipTo(currentNode.getNode(), new NCBITaxonParentRel(null));

                    } else {

                        manager.getReferenceNode().createRelationshipTo(currentNode.getNode(), new NCBIMainTaxonRel(null));

                    }

                    txnCounter++;
                    if (txnCounter % txnLimitForCommit == 0) {
                        //commiting and 'restarting' transaction
                        txn.success();
                        txn.finish();
                        txn = manager.beginTransaction();
                    }

                }

                txn.success();
                txn.finish();
                txn = manager.beginTransaction();
                logger.log(Level.INFO, "Done!");

                if (associateUniprotTaxonomy) {
                    
                    logger.log(Level.INFO, "Associating uniprot taxonomy...");
                    associateTaxonomy(nodeRetriever.getMainTaxon(), nodeRetriever, new NCBITaxonRel(null));
                    logger.log(Level.INFO, "Done!");
                }


                logger.log(Level.INFO, "reading merged file...");
                //------------reading merged file-----------------
                reader = new BufferedReader(new FileReader(mergedDumpFile));
                while ((line = reader.readLine()) != null) {

                    String[] columns = line.split("\\|");

                    String oldId = columns[0].trim();
                    String goodId = columns[1].trim();

                    NCBITaxonNode goodNode = nodeRetriever.getNCBITaxonByTaxId(goodId);
                    //indexing the node..
                    manager.getNCBITaxonIdIndex().add(goodNode.getNode(), NCBITaxonNode.NCBI_TAXON_ID_INDEX, oldId);

                    txnCounter++;
                    if (txnCounter % txnLimitForCommit == 0) {
                        //commiting and 'restarting' transaction
                        txn.success();
                        txn.finish();
                        txn = manager.beginTransaction();
                    }


                }
                reader.close();
                logger.log(Level.INFO, "done!");

                txn.success();

            } catch (Exception ex) {
                Logger.getLogger(ImportNCBITaxonomy.class.getName()).log(Level.SEVERE, null, ex);

                txn.failure();

            } finally {

                //commiting transaction
                txn.finish();

                //closing logger file handler
                fh.close();
                logger.log(Level.INFO, "Closing up inserter and index service....");
                // shutdown, makes sure all changes are written to disk
                manager.shutDown();
            }

        }
    }

    private static void associateTaxonomy(TaxonNode taxonNode,
            NodeRetriever nodeRetriever,
            NCBITaxonRel nCBITaxonRel) {

        List<OrganismNode> organisms = taxonNode.getOrganisms();

        if (!organisms.isEmpty()) {
            for (OrganismNode tempOrg : organisms) {
                Node ncbiNode = nodeRetriever.getNCBITaxonByTaxId(tempOrg.getNcbiTaxonomyId()).getNode();
                tempOrg.getNode().createRelationshipTo(ncbiNode, nCBITaxonRel);
            }
        } else {
            for (TaxonNode tempTaxon : taxonNode.getChildren()) {
                associateTaxonomy(tempTaxon, nodeRetriever, nCBITaxonRel);
            }
        }

    }
}
