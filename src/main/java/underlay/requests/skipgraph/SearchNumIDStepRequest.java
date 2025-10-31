package underlay.requests.skipgraph;

import underlay.requests.GenericRequest;
import underlay.requests.RequestType;

public class SearchNumIDStepRequest extends GenericRequest {
    public final int curNumID;
    public final int searchTarget;
    public final int level;
    public final int dir;

    public SearchNumIDStepRequest(int curNumID, int searchTarget, int level, int dir) {
        super(RequestType.SearchNumIDStepRequest);
        this.curNumID = curNumID;
        this.searchTarget = searchTarget;
        this.level = level;
        this.dir = dir;
    }
}