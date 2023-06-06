package org.apache.bookkeeper.net;


import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Test cases for NetworkTopologyImpl class 
 * 
 * @author Enrico D'Alessandro - University of Rome Tor Vergata
 */
@RunWith(Enclosed.class)
public class NetworkTopologyImplTests {

    /**
     * Test cases that stimulate the "add" scenario
     */
    @RunWith(Parameterized.class)
    public static class AddNodetest {
        
        private NodeType nodeType;
        private String nodeName;
        private String nodeLocation;
        private String rack;
        private int expectedLeaves;
        private boolean expectedException;
        
         public AddNodetest(NodeType nodeType, String nodeName, String nodeLocation, String rack, int expectedLeaves, boolean expectedException) {
             configure(nodeType, nodeName, nodeLocation, rack, expectedLeaves, expectedException);
         }

         public void configure(NodeType nodeType, String nodeName, String nodeLocation, String rack, int expectedLeaves, boolean expectedException) {
             this.nodeType = nodeType;
             this.nodeName = nodeName;
             this.nodeLocation = nodeLocation;
             this.rack = rack;
             this.expectedLeaves = expectedLeaves;
             this.expectedException = expectedException;
        }

        /**
         * BOUNDARY VALUE ANALYSIS
         *  - nodeType:             [NULL, NODE_BASE, INNER_NODE, BOOKIE_NODE]
         *  - nodeName:             [valid, invalid] (n.b. null is valid!)
         *  - nodeLocation:         [valid_root, valid_other, invalid] (root is "" or null and in this case is invalid)
         *  - rack:                 [valid_same_inserted, valid_other]
         *  - expectedLeaves:       [0, 1]
         *  - expectedException:    [true, false]
         */
        @Parameterized.Parameters
        public static Collection<Object[]> getParameter() {
            String validLocations[] = new String[] {
                    NodeBase.PATH_SEPARATOR_STR + "test-rack-1",
                    NodeBase.PATH_SEPARATOR_STR + "test-rack-2"
            };
            
            String invalidLocations[] = new String[] {
                "not-start-with-sep",
                NodeBase.ROOT // root ""
            };
            
            String validName = "test-node";
            String invalidName = NodeBase.PATH_SEPARATOR_STR + "invalid-name";
            
            return Arrays.asList(new Object[][] {
                // NODE_TYPE                NODE_NAME       NODE_LOCATION           RACK                EXPECTED_LEAVES     EXPECTED_EXCEPTION
                {  NodeType.BOOKIE_NODE,    validName,      validLocations[0],      validLocations[0],  1,                  false   },
                {  NodeType.NULL,           null,           null,                   validLocations[0],  0,                  false   },
                {  NodeType.INNER_NODE,     validName,      validLocations[0],      validLocations[0],  0,                  true    },
                {  NodeType.NODE_BASE,      validName,      validLocations[0],      validLocations[0],  1,                  false   },
                {  NodeType.NODE_BASE,      invalidName,    validLocations[1],      validLocations[1],  1,                  true    },
                {  NodeType.NODE_BASE,      null,           validLocations[0],      validLocations[1],  0,                  false   },
                {  NodeType.NODE_BASE,      validName,      invalidLocations[0],    validLocations[0],  0,                  true    },
                {  NodeType.NODE_BASE,      validName,      invalidLocations[1],    validLocations[1],  0,                  true    }
            });
        }
        
        private Node getNodeToBeAdded() {
            switch (this.nodeType) {
                case NODE_BASE:
                    return new NodeBase(this.nodeName, this.nodeLocation);
                case INNER_NODE:
                    return new NetworkTopologyImpl.InnerNode(this.nodeName, this.nodeLocation);
                case BOOKIE_NODE:
                    return new BookieNode(BookieId.parse(this.nodeName), this.nodeLocation);
                default:
                    return null;
            }
        }
        
        @Test
        public void testAddNode() {
        	try {
                NetworkTopology networkTopology = new NetworkTopologyImpl();
                Node node = getNodeToBeAdded();
                int expectedRackNumber = networkTopology.getNumOfRacks() + 1;
                networkTopology.add(node);

                Set<Node> nodes = networkTopology.getLeaves(this.rack);
                Assert.assertEquals("Wrong number of leaves detected", this.expectedLeaves, nodes.size());
                if (!NodeType.NULL.equals(this.nodeType)) {
                	Assert.assertEquals("Number of racks should be increased", expectedRackNumber, networkTopology.getNumOfRacks());
                	Assert.assertTrue("Node should be contained inside the topology", networkTopology.contains(node));
                }
            } catch (IllegalArgumentException e) {
                Assert.assertTrue("IllegalArgumentException should have been thrown", this.expectedException);
            }
        }
    }
    
    /*
     * Additional test cases to increase the coverage of the add(node) method
     * come through the white-box analysis
     */
    public static class OtherAddScenariosTests {
        
        @Test
        public void testAddRackAndNonRackSameLevel() {
            NetworkTopology networkTopology = new NetworkTopologyImpl();
            Node validNode = null;
            Node invalidNode = null;
            try {
                validNode = new NodeBase("test-node-1", NodeBase.PATH_SEPARATOR_STR + "rack0");
                invalidNode = new NodeBase("test-node-2", NodeBase.PATH_SEPARATOR_STR + "rack0/sec1");
                networkTopology.add(validNode);
            } catch (Exception e) {
                Assert.fail("This setup phase should not raise an exception");
            }

            try {
                networkTopology.add(invalidNode);
                Assert.fail("An \"InvalidTopologyException\" should be raised");
            } catch (NetworkTopologyImpl.InvalidTopologyException e) {
                Assert.assertTrue(true);
            }
        }
        
        @Test
        public void testIllegalNetworkLocation() {
            NetworkTopologyImpl networkTopologyImpl = spy(NetworkTopologyImpl.class);
            Node node = new NodeBase("test-node", NodeBase.PATH_SEPARATOR_STR + "rack0");
            when(networkTopologyImpl.getNodeForNetworkLocation(node)).thenReturn(new NodeBase("test-node-2", NodeBase.PATH_SEPARATOR_STR + "rack0"));

            try {
                networkTopologyImpl.add(node);
                Assert.fail("An \"IllegalArgumentException\" should be raised");
            } catch (IllegalArgumentException e) {
                Assert.assertTrue(true);
            }
        }
        
        @Test
        public void testAddTwoTimesSameNode() {
            NetworkTopology networkTopology = new NetworkTopologyImpl();
            final String rack = NodeBase.PATH_SEPARATOR_STR + "rack0";

            Node node = new NodeBase("test-node", rack);
            networkTopology.add(node);
            int oldSize = networkTopology.getLeaves(rack).size();

            networkTopology.add(node);
            int newSize = networkTopology.getLeaves(rack).size();

            Assert.assertEquals("The node should not be added the second time", oldSize, newSize);
        }
        
        @Test
        public void testAddTwoValidNodes() {
            NetworkTopology networkTopology = new NetworkTopologyImpl();

            Node validNode1 = new NodeBase("test-node-1", NodeBase.PATH_SEPARATOR_STR + "rack0");
            Node validNode2 = new NodeBase("test-node-2", NodeBase.PATH_SEPARATOR_STR + "rack1");

            networkTopology.add(validNode1);
            networkTopology.add(validNode2);

            Assert.assertTrue("The node is valid, so it should be inserted (1)", networkTopology.contains(validNode1));
            Assert.assertTrue("The node is valid, so it should be inserted (2)", networkTopology.contains(validNode2));
        }

        @Test
        public void testNodeWithExplicitParent() {
            Node parent = new NodeBase("parent", NodeBase.PATH_SEPARATOR_STR + "/rack0");
            Node child = new NodeBase("child", NodeBase.PATH_SEPARATOR_STR + "/rack0", parent, 1);
            NetworkTopology nt = new NetworkTopologyImpl();

            Assert.assertFalse("This node has not been added", nt.contains(child));
        }
        
    }
    
    /**
     * Test cases that stimulate different types of nodes removal
     * (e.g. already inserted, not yet inserted, not removable)
     */
    @RunWith(Parameterized.class)
    public static class RemoveNodeTest {

        private Node nodeToBeAdded;
        private RemovalTypes typeOfRemoval;

        private NetworkTopology networkTopology;

        public RemoveNodeTest(RemovalTypes typeOfRemoval) {
            configure(typeOfRemoval);
        }

        public void configure(RemovalTypes typeOfRemoval) {
            this.typeOfRemoval = typeOfRemoval;
            this.networkTopology = new NetworkTopologyImpl();
        }
        
        @Before
        public void addInitialNode() {
            try {
                this.nodeToBeAdded = new NodeBase("test-node", NodeBase.PATH_SEPARATOR_STR + "test-rack");
                networkTopology.add(this.nodeToBeAdded);
            } catch (IllegalArgumentException e) {
                Assert.fail("This test assumes that node to be inserted initially is valid");
            }
        }
        
        /**
         * BOUNDARY VALUE ANALYSIS
         * - typeOfRemoval [ADDED, NOT_ADDED, INNER]
         */
        @Parameterized.Parameters
        public static Collection<Object[]> testCasesTuples() {
                return Arrays.asList(new Object[][]{
                        // TYPE_OF_REMOVAL
                        {  RemovalTypes.ADDED      },
                        {  RemovalTypes.NOT_ADDED  },
                        {  RemovalTypes.INNER      }
                });
        }

        private Node getNodeToBeRemoved() {
            Node nodeToBeRemoved = null;
            switch (this.typeOfRemoval) {
                case ADDED:
                    nodeToBeRemoved = this.nodeToBeAdded;
                    break;
                case INNER:
                    nodeToBeRemoved = new NetworkTopologyImpl.InnerNode(NetworkTopologyImpl.InnerNode.ROOT);
                    break;
                case NOT_ADDED:
                    try {
                        nodeToBeRemoved = new NodeBase(
                                this.nodeToBeAdded.getName() + "-new",
                                this.nodeToBeAdded.getNetworkLocation() + "-new"
                        );
                    } catch (IllegalArgumentException e) {
                        Assert.fail("This test assumes that node to be inserted initially is valid (or null)");
                    }
            }

            return nodeToBeRemoved;
        }
        
        @Test
        public void testRemoveNode() {
            try {
                Node nodeToBeRemoved = getNodeToBeRemoved();

                int oldSize = this.networkTopology.getLeaves(nodeToBeAdded.getNetworkLocation()).size();
                this.networkTopology.remove(nodeToBeRemoved);
                int newSize = this.networkTopology.getLeaves(nodeToBeAdded.getNetworkLocation()).size();

                switch (this.typeOfRemoval) {
                    case ADDED:
                        Assert.assertEquals("The number of leaves should be decreased", oldSize-1, newSize);
                        break;
                    case INNER:
                        Assert.fail("It is not possible to remove inner node");
                        break;
                    case NOT_ADDED:
                        Assert.assertEquals("The number of leaves should not be decreased", oldSize, newSize);
                        break;
                }
            } catch (IllegalArgumentException e) {
                Assert.assertTrue("It is not possible to remove inner node", RemovalTypes.INNER.equals(this.typeOfRemoval));
            }            
        }
    }
    
    
    /*
     * Additional test cases to increase the coverage of the remove(node) method
     * come through the white-box analysis
     */
    public static class OtherRemoveScenariosTests {
        
        @Test
        public void testRemoveNullNode() {
            try {
                NetworkTopology networkTopology = new NetworkTopologyImpl();
                networkTopology.remove(null);
                Assert.assertTrue(true);
            } catch (Exception e) {
                Assert.fail("No exception should be raised");
            }
        }
        
        @Test
        public void testIfRackNullNumOfRackShouldNotChange() {
            NetworkTopology networkTopology = spy(NetworkTopologyImpl.class);
            // Intercept the getNode method and return a new InnerNode
            // Observe that the inner node is not added to the topology
            when(networkTopology.getNode(notNull())).thenReturn(new NetworkTopologyImpl.InnerNode("core", NodeBase.PATH_SEPARATOR_STR + "rack0"));
            
            Node node = new NodeBase("test-node", NodeBase.PATH_SEPARATOR_STR + "rack0");
            networkTopology.add(node);
            
            int oldNumOfRacks = networkTopology.getNumOfRacks();
            networkTopology.remove(node);
            int newNumOfRacks = networkTopology.getNumOfRacks();
            
            Assert.assertEquals("Number of racks should not change if rack is not null", oldNumOfRacks, newNumOfRacks);
        }
        
    }    
    
    private enum RemovalTypes {
        INNER,
        ADDED,
        NOT_ADDED
    }
    
    private enum NodeType {
        NULL,
        NODE_BASE,
        INNER_NODE,
        BOOKIE_NODE
    }
    
}
