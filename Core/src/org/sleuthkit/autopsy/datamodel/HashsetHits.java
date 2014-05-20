/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011-2014 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.datamodel;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import java.util.logging.Level;

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskException;

/**
 * Hash set hits node support.  Inner classes have all of the nodes in the tree.
 */
public class HashsetHits implements AutopsyVisitableItem {

    private static final String HASHSET_HITS = BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT.getLabel();
    private static final String DISPLAY_NAME = BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT.getDisplayName();
    private static final Logger logger = Logger.getLogger(HashsetHits.class.getName());
    private SleuthkitCase skCase;
    private final HashsetResults hashsetResults;
   
    public HashsetHits(SleuthkitCase skCase) {
        this.skCase = skCase;
        hashsetResults = new HashsetResults();
    }
    

    @Override
    public <T> T accept(AutopsyItemVisitor<T> v) {
        return v.visit(this);
    }
    
    /**
     * Stores all of the hashset results in a single class that is observable for the 
     * child nodes
     */
    private class HashsetResults extends Observable {
        // maps hashset name to list of artifacts for that set
        private final Map<String, Set<Long>> hashSetHitsMap = new LinkedHashMap<>();
        
        HashsetResults() {
            update();
        }
        
        List<String> getSetNames() {
            List<String> names = new ArrayList<>(hashSetHitsMap.keySet());
            Collections.sort(names);
            return names;
        }
        
        Set<Long> getArtifactIds(String hashSetName) {
            return hashSetHitsMap.get(hashSetName);
        }
        
        final void update() {
            hashSetHitsMap.clear();
            
            if (skCase == null) {
                return;   
            }
            
            ResultSet rs = null;
            try {
                int setNameId = ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID();
                int artId = ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID();
                String query = "SELECT value_text,blackboard_attributes.artifact_id,attribute_type_id " //NON-NLS
                        + "FROM blackboard_attributes,blackboard_artifacts WHERE " //NON-NLS
                        + "attribute_type_id=" + setNameId //NON-NLS
                        + " AND blackboard_attributes.artifact_id=blackboard_artifacts.artifact_id" //NON-NLS
                        + " AND blackboard_artifacts.artifact_type_id=" + artId; //NON-NLS
                rs = skCase.runQuery(query);
                while (rs.next()) {
                    String setName = rs.getString("value_text"); //NON-NLS
                    long artifactId = rs.getLong("artifact_id"); //NON-NLS
                    if (!hashSetHitsMap.containsKey(setName)) {
                        hashSetHitsMap.put(setName, new HashSet<Long>());
                    }
                    hashSetHitsMap.get(setName).add(artifactId);
                }
            } catch (SQLException ex) {
                logger.log(Level.WARNING, "SQL Exception occurred: ", ex); //NON-NLS
            } finally {
                if (rs != null) {
                    try {
                        skCase.closeRunQuery(rs);
                    } catch (SQLException ex) {
                        logger.log(Level.WARNING, "Error closing result set after getting hashset hits", ex); //NON-NLS
                    }
                }
            }
            setChanged();
            notifyObservers();
        }
    }

    /**
     * Top-level node for all hash sets
     */
    public class RootNode extends DisplayableItemNode {

        public RootNode() {
            super(Children.create(new HashsetNameFactory(), true), Lookups.singleton(DISPLAY_NAME));
            super.setName(HASHSET_HITS);
            super.setDisplayName(DISPLAY_NAME);
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/hashset_hits.png"); //NON-NLS
        }

        @Override
        public boolean isLeafTypeNode() {
            return false;
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> v) {
            return v.visit(this);
        }

        @Override
        protected Sheet createSheet() {
            Sheet s = super.createSheet();
            Sheet.Set ss = s.get(Sheet.PROPERTIES);
            if (ss == null) {
                ss = Sheet.createPropertiesSet();
                s.put(ss);
            }

            ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "HashsetHits.createSheet.name.name"),
                    NbBundle.getMessage(this.getClass(), "HashsetHits.createSheet.name.displayName"),
                    NbBundle.getMessage(this.getClass(), "HashsetHits.createSheet.name.desc"),
                    getName()));

            return s;
        }
    }

    /**
     * Creates child nodes for each hashset name
     */
    private class HashsetNameFactory extends ChildFactory.Detachable<String> implements Observer {

        /* This should probably be in the HashsetHits class, but the factory has nice methods
         * for its startup and shutdown, so it seemed like a cleaner place to register the
         * property change listener.
         */
        private final PropertyChangeListener pcl = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                String eventType = evt.getPropertyName();
                
                if (eventType.equals(IngestManager.IngestModuleEvent.DATA_ADDED.toString())) {
                    if (((ModuleDataEvent) evt.getOldValue()).getArtifactType() == ARTIFACT_TYPE.TSK_HASHSET_HIT) {
                        hashsetResults.update();
                    }
                }
                else if (eventType.equals(IngestManager.IngestJobEvent.COMPLETED.toString())
                || eventType.equals(IngestManager.IngestJobEvent.CANCELLED.toString())) {
                    hashsetResults.update();
                }
                else if (eventType.equals(Case.Events.CURRENT_CASE.toString())) {
                    // case was closed. Remove listeners so that we don't get called with a stale case handle
                    if (evt.getNewValue() == null) {
                        removeNotify();
                        skCase = null;
                    }
                }
            }
        };

        @Override
        protected void addNotify() {
            IngestManager.getInstance().addIngestJobEventListener(pcl);
            IngestManager.getInstance().addIngestModuleEventListener(pcl);
            Case.addPropertyChangeListener(pcl);
            hashsetResults.update();
            hashsetResults.addObserver(this);
        }

        @Override
        protected void removeNotify() {
            IngestManager.getInstance().removeIngestJobEventListener(pcl);
            IngestManager.getInstance().removeIngestModuleEventListener(pcl);
            Case.removePropertyChangeListener(pcl);
            hashsetResults.deleteObserver(this);
        }
        
        @Override
        protected boolean createKeys(List<String> list) {   
            list.addAll(hashsetResults.getSetNames());
            return true;
        }

        @Override
        protected Node createNodeForKey(String key) {
            return new HashsetNameNode(key);
        }

        @Override
        public void update(Observable o, Object arg) {
            refresh(true);
        }
    }

    /**
     * Node for a hash set name
     */
    public class HashsetNameNode extends DisplayableItemNode implements Observer {
        private final String hashSetName;
        public HashsetNameNode(String hashSetName) {
            super(Children.create(new HitFactory(hashSetName), true), Lookups.singleton(hashSetName));
            super.setName(hashSetName);
            this.hashSetName = hashSetName;
            updateDisplayName();
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/hashset_hits.png"); //NON-NLS
            hashsetResults.addObserver(this);
        }
        
        /**
         * Update the count in the display name
         */
        private void updateDisplayName() {
            super.setDisplayName(hashSetName + " (" + hashsetResults.getArtifactIds(hashSetName).size() + ")");  
        }

        @Override
        public boolean isLeafTypeNode() {
            return true;
        }

        @Override
        protected Sheet createSheet() {
            Sheet s = super.createSheet();
            Sheet.Set ss = s.get(Sheet.PROPERTIES);
            if (ss == null) {
                ss = Sheet.createPropertiesSet();
                s.put(ss);
            }

            ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "HashsetHits.createSheet.name.name"),
                    NbBundle.getMessage(this.getClass(), "HashsetHits.createSheet.name.displayName"),
                    NbBundle.getMessage(this.getClass(), "HashsetHits.createSheet.name.desc"),
                    getName()));

            return s;
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> v) {
            return v.visit(this);
        }

        @Override
        public void update(Observable o, Object arg) {
            updateDisplayName();
        }
    }

    /**
     * Creates the nodes for the hits in a given set.
     */
    private class HitFactory extends ChildFactory.Detachable<Long> implements Observer {
        private String hashsetName;
        
        private HitFactory(String hashsetName) {
            super();
            this.hashsetName = hashsetName;
        }
                
        @Override
        protected void addNotify() {
            hashsetResults.addObserver(this);
        }

        @Override
        protected void removeNotify() {
            hashsetResults.deleteObserver(this);
        }

        @Override
        protected boolean createKeys(List<Long> list) {
            list.addAll(hashsetResults.getArtifactIds(hashsetName));
            return true;
        }

        @Override
        protected Node createNodeForKey(Long id) {
            if (skCase == null) {
                return null;            
            }
            
            try {
                BlackboardArtifact art = skCase.getBlackboardArtifact(id);
                return new BlackboardArtifactNode(art);
            } catch (TskException ex) {
                logger.log(Level.WARNING, "TSK Exception occurred", ex); //NON-NLS
            }
            return null;
        }
        
        @Override
        public void update(Observable o, Object arg) {
            refresh(true);
        }
    }
}