/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.era7.bioinfo.bio4j.programs;

import com.era7.bioinfo.bio4j.CommonData;
import com.era7.bioinfo.bio4jmodel.nodes.ncbi.NCBITaxonNode;
import com.era7.bioinfo.bio4jmodel.relationships.ncbi.NCBIMainTaxonRel;
import com.era7.bioinfo.bio4jmodel.relationships.ncbi.NCBITaxonParentRel;
import com.era7.bioinfo.bio4jmodel.util.Bio4jManager;
import com.era7.bioinfo.bio4jmodel.util.NodeRetriever;
import com.era7.lib.bioinfo.bioinfoutil.Executable;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
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

        if (args.length != 2) {
            System.out.println("This program expects two parameters: \n"
                    + "1. Nodes DMP filename \n"
                    + "2. Names DMP filename \n");
        } else {
            
            Bio4jManager manager = null;

            try {

                // This block configure the logger with handler and formatter
                fh = new FileHandler("ImportNCBITaxonomy.log", true);
                SimpleFormatter formatter = new SimpleFormatter();
                fh.setFormatter(formatter);
                logger.addHandler(fh);
                logger.setLevel(Level.ALL);

                File nodesDumpFile = new File(args[0]);
                File namesDumpFile = new File(args[1]);

                logger.log(Level.INFO, "creating manager...");
                manager = new Bio4jManager(CommonData.DATABASE_FOLDER);
                NodeRetriever nodeRetriever = new NodeRetriever(manager);


                BufferedReader reader = new BufferedReader(new FileReader(nodesDumpFile));
                String line = null;


                HashMap<String, String> nodeParentMap = new HashMap<String, String>();

                logger.log(Level.INFO, "reading nodes file...");

                while ((line = reader.readLine()) != null) {

                    if (line.trim().length() > 0) {

                        String[] columns = line.split("\\|");

                        NCBITaxonNode node = new NCBITaxonNode(manager.createNode());

                        node.setTaxId(columns[0].trim());
                        node.setRank(columns[2].trim());
                        node.setEmblCode(columns[3].trim());

                        //indexing the node..
                        manager.getNCBITaxonIdIndex().add(node.getNode(), NCBITaxonNode.NCBI_TAXON_ID_INDEX, node.getTaxId());

                        //saving the parent of the node for later
                        nodeParentMap.put(node.getTaxId(), columns[1].trim());

                    }

                }

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
                    }

                }
                reader.close();
                logger.log(Level.INFO, "done!");
                
                logger.log(Level.INFO, "storing relationships...");
                
                Set<String> nodesSet = nodeParentMap.keySet();
                for (String nodeTaxId : nodesSet) {
                    
                    String parentTaxId = nodeParentMap.get(nodeTaxId);
                    
                    NCBITaxonNode currentNode = nodeRetriever.getNCBITaxonByTaxId(nodeTaxId);
                    
                    if(! nodeTaxId.equals(parentTaxId)){
                        
                        NCBITaxonNode parentNode = nodeRetriever.getNCBITaxonByTaxId(parentTaxId);            
                        parentNode.getNode().createRelationshipTo(currentNode.getNode(), new NCBITaxonParentRel(null));
                        
                    }else{
                        
                        manager.getReferenceNode().createRelationshipTo(currentNode.getNode(), new NCBIMainTaxonRel(null));                        
                        
                    }
                }
                
                logger.log(Level.INFO, "Done!");


            } catch (Exception ex) {
                Logger.getLogger(ImportNCBITaxonomy.class.getName()).log(Level.SEVERE, null, ex);
                
            }finally{

                //closing logger file handler
                fh.close();
                logger.log(Level.INFO, "Closing up inserter and index service....");
                // shutdown, makes sure all changes are written to disk
                manager.shutDown();
            }

        }
    }
}
