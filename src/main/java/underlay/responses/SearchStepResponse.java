package underlay.responses;

import skipGraph.NodeInfo;

public class SearchStepResponse extends GenericResponse {
    public final NodeInfo nextNode;
    public final int nextLevel;
    public final boolean done;

    public SearchStepResponse(NodeInfo nextNode, int nextLevel, boolean done) {
        this.nextNode = nextNode;
        this.nextLevel = nextLevel;
        this.done = done;
    }
}