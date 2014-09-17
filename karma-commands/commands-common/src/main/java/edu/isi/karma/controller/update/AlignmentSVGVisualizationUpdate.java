/*******************************************************************************
 * Copyright 2012 University of Southern California
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * 	http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * This code was developed by the Information Integration Group as part 
 * of the Karma project at the Information Sciences Institute of the 
 * University of Southern California.  For more information, publications, 
 * and related projects, please see: http://www.isi.edu/integration
 ******************************************************************************/

package edu.isi.karma.controller.update;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.jgrapht.graph.DirectedWeightedMultigraph;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.isi.karma.modeling.alignment.Alignment;
import edu.isi.karma.modeling.alignment.AlignmentManager;
import edu.isi.karma.rep.HNode;
import edu.isi.karma.rep.HTable;
import edu.isi.karma.rep.alignment.ColumnNode;
import edu.isi.karma.rep.alignment.DataPropertyOfColumnLink;
import edu.isi.karma.rep.alignment.LabeledLink;
import edu.isi.karma.rep.alignment.LinkKeyInfo;
import edu.isi.karma.rep.alignment.LinkType;
import edu.isi.karma.rep.alignment.Node;
import edu.isi.karma.rep.alignment.NodeType;
import edu.isi.karma.rep.alignment.ObjectPropertySpecializationLink;
import edu.isi.karma.view.VWorksheet;
import edu.isi.karma.view.VWorkspace;

public class AlignmentSVGVisualizationUpdate extends AbstractUpdate {
	private final String worksheetId;
	private final DirectedWeightedMultigraph<Node, LabeledLink> alignmentGraph;

	private static Logger logger = LoggerFactory.getLogger(AlignmentSVGVisualizationUpdate.class);

	private enum JsonKeys {
		worksheetId, alignmentId, label, id, nodeId, hNodeId, nodeType, source,
		target, linkType, sourceNodeId, targetNodeId, column,
		nodes, links, maxTreeHeight, linkStatus, linkUri, nodeDomain, isForcedByUser, edgeLinks, anchors, alignObject
	}

	private enum JsonValues {
		key, holderLink, objPropertyLink, Unassigned, FakeRoot, FakeRootLink, 
		Add_Parent, DataPropertyOfColumnHolder, horizontalDataPropertyLink
	}

	public AlignmentSVGVisualizationUpdate(String worksheetId, Alignment alignment) {
		super();
		this.worksheetId = worksheetId;
		this.alignmentGraph = alignment.getSteinerTree();
	}
	
	private JSONObject getNodeJsonObject(int id, String label, String nodeId, String nodeType
			, boolean isForcedByUser, String nodeDomain) throws JSONException {
		JSONObject nodeObj = new JSONObject();
		nodeObj.put(JsonKeys.label.name(), label);
		nodeObj.put(JsonKeys.id.name(), id);
		nodeObj.put(JsonKeys.nodeId.name(), nodeId);
		nodeObj.put(JsonKeys.nodeType.name(), nodeType);
		nodeObj.put(JsonKeys.isForcedByUser.name(), isForcedByUser);
		nodeObj.put(JsonKeys.nodeDomain.name(), nodeDomain);
		return nodeObj;
	}
	
	private JSONObject getColumnJsonObject(int id, String label, String nodeId, String nodeType
			,boolean isForcedByUser, String hNodeId, String nodeDomain, int columnIndex) throws JSONException {
		JSONObject nodeObj = getNodeJsonObject(id, label, nodeId, nodeType, isForcedByUser, nodeDomain);
		nodeObj.put(JsonKeys.hNodeId.name(), hNodeId);
		nodeObj.put(JsonKeys.column.name(), columnIndex);
		return nodeObj;
	}

	public boolean equals(Object o) {
		if (o instanceof AlignmentSVGVisualizationUpdate) {
			AlignmentSVGVisualizationUpdate t = (AlignmentSVGVisualizationUpdate)o;
			return t.worksheetId.equals(worksheetId);
		}
		return false;
	}

	@Override
	public void generateJson(String prefix, PrintWriter pw,
			VWorkspace vWorkspace) {

		VWorksheet vWorksheet =  vWorkspace.getViewFactory().getVWorksheetByWorksheetId(worksheetId);
		List<String> hNodeIdList = vWorksheet.getHeaderVisibleLeafNodes();
		
		String alignmentId = AlignmentManager.Instance().constructAlignmentId(
				vWorkspace.getWorkspace().getId(), vWorksheet.getWorksheetId());
		
		JSONObject topObj = new JSONObject();
		try {
			topObj.put(GenericJsonKeys.updateType.name(),
					AlignmentSVGVisualizationUpdate.class.getSimpleName());
			topObj.put(JsonKeys.alignmentId.name(), alignmentId);
			topObj.put(JsonKeys.worksheetId.name(), worksheetId);
			
			/*** Add the nodes and the links from the Steiner tree ***/
			JSONArray nodesArr = new JSONArray();
			JSONArray anchorsArr = new JSONArray();
			JSONArray linksArr = new JSONArray();
			JSONArray edgeLinksArr = new JSONArray();
			
			HashMap<Node, Integer> verticesIndex = new HashMap<Node, Integer>();
			HashMap<String, ColumnNode> columnNodes = new HashMap<>();
			
			if (alignmentGraph != null && alignmentGraph.vertexSet().size() != 0) {
				Set<Node> nodes = alignmentGraph.vertexSet();
				for (Node node : nodes) {
					if (node instanceof ColumnNode) {
						columnNodes.put(((ColumnNode)node).getHNodeId(), (ColumnNode)node);
					}
				}
			}
			HTable headers = vWorksheet.getWorksheet().getHeaders();
			for(int columnNum=0; columnNum<hNodeIdList.size(); columnNum++) {
				String hNodeId = hNodeIdList.get(columnNum);
				ColumnNode node = columnNodes.get(hNodeId);
				JSONObject anchorObj;
				if(node != null) {
					anchorObj = getColumnJsonObject(columnNum, node.getLocalId(), node.getId(),
							node.getType().name(), node.isForced(), hNodeId, node.getUri(), columnNum);
				} else {
					HNode hNode = headers.getHNode(hNodeId);
					anchorObj = getColumnJsonObject(columnNum, hNode.getColumnName(), hNode.getId(), "ColumnNode", false, hNodeId, "", columnNum);
				}
				anchorsArr.put(anchorObj);
				verticesIndex.put(node, columnNum);
			}
			
			
			int nodesIndexcounter = hNodeIdList.size();
			
			
			if (alignmentGraph != null && alignmentGraph.vertexSet().size() != 0) {
				/** Add the nodes **/
				Set<Node> nodes = alignmentGraph.vertexSet();
				for (Node node : nodes) {
					/** Add the semantic type information **/
					if (node instanceof ColumnNode) {
						//Already handled
					} else {
						JSONObject nodeObj = getNodeJsonObject(nodesIndexcounter, node.getLocalId(), node.getId(), 
								node.getType().name()
								, node.isForced(), node.getUri());
						nodesArr.put(nodeObj);
						verticesIndex.put(node, nodesIndexcounter++);
					}
				}
				
				/*** Add the links ***/
				Set<LabeledLink> links = alignmentGraph.edgeSet();
				for (LabeledLink link : links) {
					
					Node source = link.getSource();
					Integer sourceIndex = verticesIndex.get(source);
					Node target = link.getTarget();
					Integer targetIndex = verticesIndex.get(target);
					Set<LabeledLink> outEdges = alignmentGraph.outgoingEdgesOf(target);
					
					if(sourceIndex == null || targetIndex == null) {
						logger.error("Edge vertex index not found!");
						continue;
					}

					JSONObject linkObj = new JSONObject();
					linkObj.put(JsonKeys.source.name(), sourceIndex);
					linkObj.put(JsonKeys.target.name(), targetIndex);
					linkObj.put(JsonKeys.sourceNodeId.name(), source.getId());
					linkObj.put(JsonKeys.targetNodeId.name(), target.getId());
					
					linkObj.put(JsonKeys.label.name(), link.getLabel().getLocalName());
					linkObj.put(JsonKeys.id.name(), link.getId()+"");
					linkObj.put(JsonKeys.linkStatus.name(), link.getStatus().name());
					linkObj.put(JsonKeys.linkUri.name(), link.getLabel().getUri());

					if(target.getType() == NodeType.ColumnNode && outEdges.isEmpty()) {
						linkObj.put(JsonKeys.linkType.name(), JsonValues.holderLink.name());
						if(link.getKeyType() == LinkKeyInfo.PartOfKey)
							linkObj.put(JsonKeys.label.name(), link.getLabel().getLocalName()+"*");
					}

					linkObj.put(JsonKeys.linkType.name(), link.getType());
					if(link.getType() == LinkType.ObjectPropertySpecializationLink) {
						ObjectPropertySpecializationLink spLink = (ObjectPropertySpecializationLink)link;
						String linkId = spLink.getSpecializedLinkId();
						linkObj.put(JsonKeys.source.name(), linkId);
						edgeLinksArr.put(linkObj);
					} else if(link.getType() == LinkType.DataPropertyOfColumnLink) {
						DataPropertyOfColumnLink spLink = (DataPropertyOfColumnLink)link;
						String linkId = spLink.getSpecializedLinkId();
						linkObj.put(JsonKeys.source.name(), linkId);
						edgeLinksArr.put(linkObj);
					} else {
						linksArr.put(linkObj);
					}
				}
			} 

			JSONObject alignObject = new JSONObject();
			alignObject.put(JsonKeys.anchors.name(), anchorsArr);
			alignObject.put(JsonKeys.nodes.name(), nodesArr);
			alignObject.put(JsonKeys.links.name(), linksArr);
			alignObject.put(JsonKeys.edgeLinks.name(), edgeLinksArr);
			
			topObj.put(JsonKeys.alignObject.name(), alignObject);
			
			pw.write(topObj.toString());
		} catch (JSONException e) {
			logger.error("Error occured while writing JSON!", e);
		}
	
		
	}
}
