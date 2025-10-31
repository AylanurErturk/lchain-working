package underlay.responses;

import skipGraph.NodeInfo;
import java.util.List;

public class NodeInfoPageResponse extends GenericResponse {
    public final List<NodeInfo> items;
    public final boolean hasMore;
    public final int nextOffset;

    public NodeInfoPageResponse(List<NodeInfo> items, boolean hasMore, int nextOffset) {
        this.items = items;
        this.hasMore = hasMore;
        this.nextOffset = nextOffset;
    }
}