package underlay.requests.skipgraph;

import underlay.requests.RequestType;

public class SearchByNumIDRequest extends GenericSkipGraphRequest {
  public final int num;
  public final int shardID;

  public SearchByNumIDRequest(int num, int shardID) {
    super(RequestType.SearchByNumIDRequest);
    this.num = num;
    this.shardID = shardID;
  }
}
