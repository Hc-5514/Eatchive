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

    class PlaceApiException extends RuntimeException {
        public PlaceApiException(String message) {
            super(message);
        }

        public PlaceApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
