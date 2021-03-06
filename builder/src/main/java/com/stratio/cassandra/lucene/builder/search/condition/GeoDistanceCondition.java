/*
 * Licensed to STRATIO (C) under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.  The STRATIO (C) licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.stratio.cassandra.lucene.builder.search.condition;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;

/**
 * A {@link Condition} that matches documents containing a point contained between two certain circles.
 *
 * @author Andres de la Pena {@literal <adelapena@stratio.com>}
 */
public class GeoDistanceCondition extends Condition {

    /** The name of the field to be matched. */
    @JsonProperty("field")
    final String field;

    /** The latitude of the reference point. */
    @JsonProperty("latitude")
    final double latitude;

    /** The longitude of the reference point. */
    @JsonProperty("longitude")
    final double longitude;

    /** The max allowed distance. */
    @JsonProperty("max_distance")
    final String maxDistance;

    /** The min allowed distance. */
    @JsonProperty("min_distance")
    String minDistance;

    /**
     * Returns a new {@link GeoDistanceCondition} with the specified field reference point.
     *
     * @param field       The name of the field to be matched.
     * @param latitude    The latitude of the reference point.
     * @param longitude   The longitude of the reference point.
     * @param maxDistance The max allowed distance.
     */
    @JsonCreator
    public GeoDistanceCondition(@JsonProperty("field") String field,
                                @JsonProperty("latitude") double latitude,
                                @JsonProperty("longitude") double longitude,
                                @JsonProperty("max_distance") String maxDistance) {
        this.field = field;
        this.longitude = longitude;
        this.latitude = latitude;
        this.maxDistance = maxDistance;
    }

    /**
     * Sets the min allowed distance.
     *
     * @param minDistance The min allowed distance.
     * @return This.
     */
    public GeoDistanceCondition minDistance(String minDistance) {
        this.minDistance = minDistance;
        return this;
    }
}
