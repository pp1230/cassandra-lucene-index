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

package com.stratio.cassandra.lucene;

import com.stratio.cassandra.lucene.index.LuceneIndex;
import com.stratio.cassandra.lucene.mapping.Mapper;
import com.stratio.cassandra.lucene.util.TaskQueue;
import org.apache.cassandra.config.CFMetaData;
import org.apache.cassandra.db.Clustering;
import org.apache.cassandra.db.ColumnFamilyStore;
import org.apache.cassandra.db.DecoratedKey;
import org.apache.cassandra.db.SinglePartitionReadCommand;
import org.apache.cassandra.db.filter.ClusteringIndexNamesFilter;
import org.apache.cassandra.db.filter.ColumnFilter;
import org.apache.cassandra.db.rows.Row;
import org.apache.cassandra.db.rows.UnfilteredRowIterator;
import org.apache.cassandra.schema.IndexMetadata;
import org.apache.cassandra.utils.concurrent.OpOrder;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;

import static java.util.stream.Collectors.toCollection;

/**
 * Class for providing operations between Cassandra and Lucene.
 *
 * @author Andres de la Pena {@literal <adelapena@stratio.com>}
 */
public class IndexService {

    protected static final Logger logger = LoggerFactory.getLogger(IndexService.class);

    private final ColumnFamilyStore table;
    private final CFMetaData tableMetadata;
    private final IndexMetadata indexMetadata;
    private final LuceneIndex lucene;
    private final Mapper mapper;
    private final String indexName;

    private final TaskQueue indexQueue;

    /**
     * Constructor using the specified {@link IndexOptions}.
     *
     * @param table The indexed table.
     * @param indexMetadata The index metadata.
     */
    public IndexService(ColumnFamilyStore table, IndexMetadata indexMetadata) {
        this.table = table;
        this.indexMetadata = indexMetadata;
        tableMetadata = table.metadata;
        indexName = String.format("%s.%s.%s", tableMetadata.ksName, tableMetadata.cfName, indexMetadata.name);
        String mbean = String.format("com.stratio.cassandra.lucene:type=LuceneIndexes,keyspace=%s,table=%s,index=%s",
                                     tableMetadata.ksName,
                                     tableMetadata.cfName,
                                     indexName);
        IndexOptions options = new IndexOptions(tableMetadata, indexMetadata);
        lucene = new LuceneIndex(mbean,
                                 indexName,
                                 options.path,
                                 options.schema.getAnalyzer(),
                                 options.refreshSeconds,
                                 options.ramBufferMB,
                                 options.maxMergeMB,
                                 options.maxCachedMB);
        mapper = new Mapper(tableMetadata, options.schema);
        indexQueue = new TaskQueue(options.indexingThreads, options.indexingThreads);
    }

    /**
     * Returns the full qualified name of the index.
     *
     * @return the index name
     */
    public String getIndexName() {
        return indexName;
    }

    /**
     * Commits the pending changes.
     */
    public final void commit() {
        indexQueue.submitSynchronous(lucene::commit);
    }

    /**
     * Deletes all the index contents.
     */
    public final void truncate() {
        indexQueue.submitSynchronous(lucene::truncate);
    }

    /**
     * Closes and removes all the index files.
     */
    public final void delete() {
        indexQueue.shutdown();
        lucene.delete();
    }

    /**
     * Indexes the specified rows. The rows are read from the indexed table and sent to the Lucene index. The indexing
     * can be synchronous or asynchronous depending on the configuration options.
     *
     * @param key a partition key
     * @param rows a list of rows to be indexed
     * @param deletePartition if the partition should be deleted from the index before indexing
     * @param nowInSec the current time in seconds
     * @param opGroup a group of identically ordered operations
     */
    public void index(DecoratedKey key,
                      List<Row> rows,
                      boolean deletePartition,
                      int nowInSec,
                      OpOrder.Group opGroup) {
        if (deletePartition) {
            Term term = mapper.term(key);
            indexQueue.submitAsynchronous(key, () -> lucene.delete(term));
        }
        if (!rows.isEmpty()) {
            NavigableSet<Clustering> clusterings = rows.parallelStream()
                                                       .filter(row -> !row.isStatic())
                                                       .map(Row::clustering)
                                                       .collect(toCollection(this::clusterings));
            UnfilteredRowIterator iterator = rows(key, clusterings, nowInSec, opGroup);
            while (iterator.hasNext()) {
                Row row = (Row) iterator.next();
                if (row.hasLiveData(nowInSec)) {
                    Term term = mapper.term(key);
                    Document document = mapper.document(key, row);
                    indexQueue.submitAsynchronous(key, () -> lucene.upsert(term, document));
                } else {
                    Term term = mapper.term(key, row);
                    indexQueue.submitAsynchronous(key, () -> lucene.delete(term));
                }
            }
        }
    }

    private UnfilteredRowIterator rows(DecoratedKey key,
                                       NavigableSet<Clustering> clusterings,
                                       int nowInSec,
                                       OpOrder.Group opGroup) {
        ClusteringIndexNamesFilter filter = new ClusteringIndexNamesFilter(clusterings, false);
        ColumnFilter columnFilter = ColumnFilter.all(tableMetadata);
        return SinglePartitionReadCommand.create(tableMetadata, nowInSec, key, columnFilter, filter)
                                         .queryMemtableAndDisk(table, opGroup);
    }

    private NavigableSet<Clustering> clusterings() {
        return new TreeSet<>(tableMetadata.comparator);
    }
}