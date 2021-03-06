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

package com.stratio.cassandra.lucene.testsAT.udt;

import com.datastax.driver.core.TupleType;
import com.stratio.cassandra.lucene.testsAT.BaseAT;
import com.stratio.cassandra.lucene.testsAT.util.CassandraUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.datastax.driver.core.DataType.*;
import static com.stratio.cassandra.lucene.builder.Builder.*;

/**
 * @author Andres de la Pena {@literal <adelapena@stratio.com>}
 */

@RunWith(JUnit4.class)
public class TupleIndexingAT extends BaseAT {

    private static CassandraUtils cassandraUtils;

    @BeforeClass
    public static void before() {
        TupleType tuple = TupleType.of(cint(), text(), cfloat());
        cassandraUtils = CassandraUtils.builder("tuple_indexing")
                                       .withColumn("k", "int")
                                       .withColumn("v", "tuple<int, text, float>")
                                       .withPartitionKey("k")
                                       .withMapper("v.0", integerMapper().sorted(true))
                                       .withMapper("v.1", stringMapper().sorted(true))
                                       .withMapper("v.2", floatMapper().sorted(true))
                                       .build()
                                       .createKeyspace()
                                       .createTable()
                                       .createIndex()
                                       .insert(new String[]{"k", "v"}, new Object[]{0, tuple.newValue(1, "foo", 2.1f)})
                                       .insert(new String[]{"k", "v"}, new Object[]{1, tuple.newValue(2, "bar", 2.2f)})
                                       .insert(new String[]{"k", "v"}, new Object[]{2, tuple.newValue(3, "zas", 1.2f)});
    }

    @AfterClass
    public static void after() {
        cassandraUtils.dropIndex().dropTable().dropKeyspace();
    }

    @Test
    public void testSearchTuple1() {
        cassandraUtils.filter(match("v.0", 1)).check(1);
        cassandraUtils.filter(match("v.0", 2)).check(1);
        cassandraUtils.filter(match("v.0", 3)).check(1);
        cassandraUtils.filter(match("v.0", 4)).check(0);
        cassandraUtils.filter(range("v.0").lower(1).includeLower(true).upper(2).includeUpper(true)).check(2);
        cassandraUtils.filter(range("v.0").lower(2).includeLower(true).upper(3).includeUpper(true)).check(2);
        cassandraUtils.filter(range("v.0").lower(3).includeLower(true).upper(4).includeUpper(true)).check(1);
        cassandraUtils.filter(range("v.0").lower(4).includeLower(true).upper(5).includeUpper(true)).check(0);
        cassandraUtils.sort(field("v.0").reverse(true)).checkIntColumn("k", 2, 1, 0);
    }

    @Test
    public void testSearchTuple2() {
        cassandraUtils.filter(match("v.1", "foo")).checkIntColumn("k", 0);
        cassandraUtils.filter(match("v.1", "bar")).checkIntColumn("k", 1);
        cassandraUtils.filter(match("v.1", "zas")).checkIntColumn("k", 2);
        cassandraUtils.sort(field("v.1")).checkIntColumn("k", 1, 0, 2);
    }

    @Test
    public void testSearchTuple3() {
        cassandraUtils.filter(match("v.2", 2.1)).checkIntColumn("k", 0);
        cassandraUtils.filter(match("v.2", 2.2)).checkIntColumn("k", 1);
        cassandraUtils.filter(match("v.2", 1.2)).checkIntColumn("k", 2);
        cassandraUtils.sort(field("v.2")).checkIntColumn("k", 2, 0, 1);
    }

}
