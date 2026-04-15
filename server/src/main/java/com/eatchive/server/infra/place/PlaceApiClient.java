package com.eatchive.server.infra.place;

import java.util.List;
import java.util.Optional;

/**
 * Contract established by SPIKE-NaverAPI for future provider implementations.
 * Implementations (NaverSearchApiClient, KakaoLocalApiClient, etc.) map upstream
 * responses into these DTOs so that domain services can remain provider-agnostic.
 */
public interface PlaceApiClient {

    /**
     * Performs an external place search.
     *
     * @param query search constraints such as keyword and pagination
     * @return normalized result with paging metadata
     */
    PlaceSearchResult searchPlaces(SearchPlacesQuery query);

    /**
     * Resolves textual addresses or coordinates into a normalized representation.
     *
     * @param query geocoding inputs (address, coordinate)
     * @return optional normalized coordinate info
     */
    Optional<GeocodeResult> geocode(GeocodeQuery query);

    record SearchPlacesQuery(
            String keyword,
            Integer page,
            Integer size,
            Double latitude,
            Double longitude,
            SortOption sortOption
    ) {
        public SearchPlacesQuery {
            if (keyword == null || keyword.isBlank()) {
                throw new IllegalArgumentException("keyword must not be blank");
            }
        }
    }

    enum SortOption {
        ACCURACY,
        DISTANCE
    }

    record PlaceSearchResult(
            List<PlaceSummary> items,
            int page,
            int size,
            boolean hasMore
    ) { }

    record PlaceSummary(
            String externalId,
            String name,
            String category,
            String address,
            String roadAddress,
            double latitude,
            double longitude,
            double distanceMeters
    ) { }

    record GeocodeQuery(
            String address,
            Double latitude,
            Double longitude
    ) {
        public GeocodeQuery {
            if ((address == null || address.isBlank()) && latitude == null && longitude == null) {
                throw new IllegalArgumentException("Either address or coordinates must be provided");
            }
        }
    }

    record GeocodeResult(
            double latitude,
            double longitude,
            String coordinateSource
    ) { }

    /**
     * Provider-agnostic external place API error categories.
     * Concrete providers must be mapped into one of these types.
     */
    enum ErrorType {
        PLACE_API_TIMEOUT,
        PLACE_API_UPSTREAM_ERROR,
        PLACE_API_RATE_LIMIT,
        PLACE_API_BAD_REQUEST
    }

    class PlaceApiException extends RuntimeException {
        private final ErrorType errorType;

        public PlaceApiException(ErrorType errorType, String message) {
            super(message);
            this.errorType = errorType;
        }

        public PlaceApiException(ErrorType errorType, String message, Throwable cause) {
            super(message, cause);
            this.errorType = errorType;
        }

        public ErrorType getErrorType() {
            return errorType;
        }
    }
}
