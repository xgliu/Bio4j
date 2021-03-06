/*
 * Copyright (C) 2010-2012  "Bio4j"
 *
 * This file is part of Bio4j
 *
 * Bio4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */
package com.era7.bioinfo.bio4j.programs;

import com.era7.bioinfo.bio4j.model.nodes.EnzymeNode;
import com.era7.lib.bioinfo.bioinfoutil.Executable;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import org.neo4j.helpers.collection.MapUtil;
import org.neo4j.unsafe.batchinsert.*;

/**
 * Imports Expasy Enzyme DB into Bio4j (everything but Uniprot associations which are imported
 * from Uniprot xml files.
 * @author Pablo Pareja Tobes <ppareja@era7.com>
 */
public class ImportEnzymeDB implements Executable {

    public static final String IDENTIFICATION_LINE_CODE = "ID";
    public static final String OFFICIAL_NAME_LINE_CODE = "DE";
    public static final String ALTERNATE_NAME_LINE_CODE = "AN";
    public static final String CATALYTIC_ACTIVITY_LINE_CODE = "CA";
    public static final String COMMENTS_LINE_CODE = "CC";
    public static final String COFACTORS_LINE_CODE = "CF";
    public static final String PROSITE_CROSS_REFERENCES_LINE_CODE = "PR";
    public static final String SWISS_PROT_CROSS_REFERENCES_LINE_CODE = "DR";
    public static final String TERMINATION_LINE_CODE = "//";
    private static final Logger logger = Logger.getLogger("ImportEnzymeDB");
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

        if (args.length != 3) {
            System.out.println("This program expects the following parameters: \n"
                    + "1. Enzyme DB data file (.dat) \n"
                    + "2. Bio4j DB folder \n"
                    + "3. Batch inserter .properties file");

        } else {

            BatchInserter inserter = null;
            BatchInserterIndexProvider indexProvider = null;

            BatchInserterIndex enzymeIdIndex = null;

            try {

                // This block configures the logger with handler and formatter
                fh = new FileHandler("ImportEnzymeDB.log", true);
                SimpleFormatter formatter = new SimpleFormatter();
                fh.setFormatter(formatter);
                logger.addHandler(fh);
                logger.setLevel(Level.ALL);

                // create the batch inserter
                inserter = BatchInserters.inserter(args[1], MapUtil.load(new File(args[2])));

                // create the batch index service
                indexProvider = new LuceneBatchInserterIndexProvider(inserter);
                Map<String, String> indexProps = MapUtil.stringMap("provider", "lucene", "type", "exact");

                enzymeIdIndex = indexProvider.nodeIndex(EnzymeNode.ENZYME_ID_INDEX, indexProps);

                //------------------node properties maps-----------------------------------
                Map<String, Object> enzymeProperties = new HashMap<String, Object>();
                enzymeProperties.put(EnzymeNode.NODE_TYPE_PROPERTY, EnzymeNode.NODE_TYPE);
                //--------------------------------------------------------------------------

                int counter = 0;

                BufferedReader reader = new BufferedReader(new FileReader(new File(args[0])));
                String line = null;

                boolean enzymeFound = false;
                String officialName = "";
                String enzymeId = "";
                String commentsSt = "";
                String catalyticActivity = "";
                List<String> alternateNames = new LinkedList<String>();
                List<String> cofactors = new LinkedList<String>();
                List<String> prositeCrossRefs = new LinkedList<String>();
                boolean deletedEntry = false;
                boolean transferredEntry = false;
                
                System.out.println("Reading file....");

                while ((line = reader.readLine()) != null) {
                    if (line.startsWith(IDENTIFICATION_LINE_CODE)) {
                        enzymeFound = true;
                        enzymeId = line.substring(5).trim();


                    } else if (enzymeFound) {

                        if (line.startsWith(OFFICIAL_NAME_LINE_CODE)) {

                            officialName += line.substring(5).trim();
                            
                            if(officialName.contains("Deleted entry.")){
                                deletedEntry = true;
                            }else if(officialName.contains("Transferred entry:")){
                                transferredEntry = true;
                            }

                        } else if (line.startsWith(ALTERNATE_NAME_LINE_CODE)) {

                            alternateNames.add(line.substring(5).trim());

                        } else if (line.startsWith(COFACTORS_LINE_CODE)) {

                            String[] cofs = line.substring(5).trim().split(";");
                            for (String cofactorSt : cofs) {
                                cofactors.add(cofactorSt.trim());
                            }

                        } else if (line.startsWith(PROSITE_CROSS_REFERENCES_LINE_CODE)) {

                            String[] proRefs = line.substring(5).trim().split(";");
                            for (String prositeSt : proRefs) {
                                if(!prositeSt.equals("PROSITE")){
                                    prositeCrossRefs.add(prositeSt.trim());
                                }                                
                            }

                        } else if (line.startsWith(COMMENTS_LINE_CODE)) {

                            commentsSt += line.substring(5).trim() + " ";

                        }else if (line.startsWith(CATALYTIC_ACTIVITY_LINE_CODE)) {

                            catalyticActivity += line.substring(5).trim() + " ";

                        } else if (line.startsWith(TERMINATION_LINE_CODE)) {
                            if (enzymeFound) {
                                
                                if(deletedEntry){
                                    logger.log(Level.INFO, ("Entry with id " + enzymeId + " was deleted. It won't be stored..."));
                                    deletedEntry = false;
                                }else if(transferredEntry){
                                    logger.log(Level.INFO, ("Entry with id " + enzymeId + " was transferred. It won't be stored..."));
                                    transferredEntry = false;
                                }
                                
                                enzymeProperties.put(EnzymeNode.ID_PROPERTY, enzymeId);
                                enzymeProperties.put(EnzymeNode.OFFICIAL_NAME_PROPERTY, officialName);
                                enzymeProperties.put(EnzymeNode.ALTERNATE_NAMES_PROPERTY, alternateNames.toArray(new String[0]));
                                enzymeProperties.put(EnzymeNode.COFACTORS_PROPERTY, cofactors.toArray(new String[0]));
                                enzymeProperties.put(EnzymeNode.PROSITE_CROSS_REFERENCES_PROPERTY, prositeCrossRefs.toArray(new String[0]));
                                enzymeProperties.put(EnzymeNode.CATALYTIC_ACTIVITY_PROPERTY, catalyticActivity);
                                enzymeProperties.put(EnzymeNode.COMMENTS_PROPERTY, commentsSt);

                                //creating node
                                long enzymeNodeId = inserter.createNode(enzymeProperties);
                                
                                //indexing node
                                enzymeIdIndex.add(enzymeNodeId, MapUtil.map(EnzymeNode.ENZYME_ID_INDEX,enzymeId));

                                counter++;
                                if (counter % 100 == 0) {
                                    System.out.println(counter + " enzymes inserted...");
                                }   
                                
                            }
                            enzymeFound = false;
                            officialName = "";
                            enzymeId = "";
                            commentsSt = "";
                            catalyticActivity = "";
                            alternateNames.clear();
                            cofactors.clear();
                            prositeCrossRefs.clear();
                        }

                    }

                }


            } catch (Exception e) {
                logger.log(Level.SEVERE, e.getMessage());
                StackTraceElement[] trace = e.getStackTrace();
                for (StackTraceElement stackTraceElement : trace) {
                    logger.log(Level.SEVERE, stackTraceElement.toString());
                }
            } finally {

                try {
                    //closing logger file handler
                    fh.close();
                    logger.log(Level.INFO, "Closing up inserter and index service....");
                    // shutdown, makes sure all changes are written to disk
                    indexProvider.shutdown();
                    inserter.shutdown();

                } catch (Exception e) {
                    logger.log(Level.SEVERE, e.getMessage());
                    StackTraceElement[] trace = e.getStackTrace();
                    for (StackTraceElement stackTraceElement : trace) {
                        logger.log(Level.SEVERE, stackTraceElement.toString());
                    }
                }

            }

        }
    }
}
