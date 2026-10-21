package search.adapters.grpc;

import search.application.SearchService;
import search.domain.SearchQuery;
import search.v1.SearchOuterClass.SearchHit;
import search.v1.SearchOuterClass.SearchRequest;
import search.v1.SearchOuterClass.SearchResponse;
import search.v1.SearchQueryGrpc.SearchQueryImplBase;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.List;

/** gRPC adapter: translates proto messages to/from the application layer. */
public class SearchGrpcService extends SearchQueryImplBase {

    private final SearchService searchService;

    public SearchGrpcService(SearchService searchService) {
        this.searchService = searchService;
    }

    @Override
    public void search(SearchRequest request, StreamObserver<SearchResponse> responseObserver) {
        try {
            List<search.domain.SearchResult> results = searchService.search(
                new SearchQuery(request.getQuery(), request.getIndex(), request.getLimit()));
            SearchResponse response = SearchResponse.newBuilder()
                .addAllHits(results.stream()
                    .map(result -> SearchHit.newBuilder()
                        .setDocId(result.docId())
                        .setScore(result.score())
                        .setSource(result.source())
                        .build())
                    .toList())
                .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                .withDescription(e.getMessage()).asException());
        }
    }
}
