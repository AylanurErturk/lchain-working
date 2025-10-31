package underlay.requests.skipgraph;
import underlay.requests.GenericRequest;
import underlay.requests.RequestType;

public class GetNodesWithNameIDPageRequest extends GenericRequest {
    public final String nameID;
    public final int offset;
    public final int limit;

    public GetNodesWithNameIDPageRequest(String nameID, int offset, int limit) {
        super(RequestType.GetNodesWithNameIDPageRequest);
        this.nameID = nameID;
        this.offset = offset;
        this.limit = limit;
    }
}
