/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elasticsearch.search.aggregations.metrics;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.aggregations.AggregationTestScriptsPlugin;
import org.elasticsearch.search.aggregations.InternalAggregation;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.global.Global;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.max.Max;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.metrics.valuecount.ValueCount;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.count;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.global;
import static org.elasticsearch.search.aggregations.AggregationBuilders.histogram;
import static org.elasticsearch.search.aggregations.AggregationBuilders.max;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertHitCount;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertSearchResponse;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

public class MaxIT extends AbstractNumericTestCase {
    @Override
    protected Collection<Class<? extends Plugin>> nodePlugins() {
        return Collections.singleton(AggregationTestScriptsPlugin.class);
    }

    @Override
    public void testEmptyAggregation() throws Exception {
        SearchResponse searchResponse = client().prepareSearch("empty_bucket_idx")
                .setQuery(matchAllQuery())
                .addAggregation(histogram("histo").field("value").interval(1L).minDocCount(0).subAggregation(max("max").field("value")))
                .execute().actionGet();

        assertThat(searchResponse.getHits().getTotalHits(), equalTo(2L));
        Histogram histo = searchResponse.getAggregations().get("histo");
        assertThat(histo, notNullValue());
        Histogram.Bucket bucket = histo.getBuckets().get(1);
        assertThat(bucket, notNullValue());

        Max max = bucket.getAggregations().get("max");
        assertThat(max, notNullValue());
        assertThat(max.getName(), equalTo("max"));
        assertThat(max.getValue(), equalTo(Double.NEGATIVE_INFINITY));
    }

    @Override
    public void testUnmapped() throws Exception {
        SearchResponse searchResponse = client().prepareSearch("idx_unmapped")
                .setQuery(matchAllQuery())
                .addAggregation(max("max").field("value"))
                .execute().actionGet();

        assertThat(searchResponse.getHits().getTotalHits(), equalTo(0L));

        Max max = searchResponse.getAggregations().get("max");
        assertThat(max, notNullValue());
        assertThat(max.getName(), equalTo("max"));
        assertThat(max.getValue(), equalTo(Double.NEGATIVE_INFINITY));
    }

    @Override
    public void testSingleValuedField() throws Exception {
        SearchResponse searchResponse = client().prepareSearch("idx")
                .setQuery(matchAllQuery())
                .addAggregation(max("max").field("value"))
                .execute().actionGet();

        assertHitCount(searchResponse, 10);

        Max max = searchResponse.getAggregations().get("max");
        assertThat(max, notNullValue());
        assertThat(max.getName(), equalTo("max"));
        assertThat(max.getValue(), equalTo(10.0));
    }

    public void testSingleValuedFieldWithFormatter() throws Exception {
        SearchResponse searchResponse = client().prepareSearch("idx").setQuery(matchAllQuery())
                .addAggregation(max("max").format("0000.0").field("value")).execute().actionGet();

        assertHitCount(searchResponse, 10);

        Max max = searchResponse.getAggregations().get("max");
        assertThat(max, notNullValue());
        assertThat(max.getName(), equalTo("max"));
        assertThat(max.getValue(), equalTo(10.0));
        assertThat(max.getValueAsString(), equalTo("0010.0"));
    }

    @Override
    public void testSingleValuedFieldGetProperty() throws Exception {
        SearchResponse searchResponse = client().prepareSearch("idx").setQuery(matchAllQuery())
                .addAggregation(global("global").subAggregation(max("max").field("value"))).execute().actionGet();

        assertHitCount(searchResponse, 10);

        Global global = searchResponse.getAggregations().get("global");
        assertThat(global, notNullValue());
        assertThat(global.getName(), equalTo("global"));
        assertThat(global.getDocCount(), equalTo(10L));
        assertThat(global.getAggregations(), notNullValue());
        assertThat(global.getAggregations().asMap().size(), equalTo(1));

        Max max = global.getAggregations().get("max");
        assertThat(max, notNullValue());
        assertThat(max.getName(), equalTo("max"));
        double expectedMaxValue = 10.0;
        assertThat(max.getValue(), equalTo(expectedMaxValue));
        assertThat((Max) ((InternalAggregation)global).getProperty("max"), equalTo(max));
        assertThat((double) ((InternalAggregation)global).getProperty("max.value"), equalTo(expectedMaxValue));
        assertThat((double) ((InternalAggregation)max).getProperty("value"), equalTo(expectedMaxValue));
    }

    @Override
    public void testSingleValuedFieldPartiallyUnmapped() throws Exception {
        SearchResponse searchResponse = client().prepareSearch("idx", "idx_unmapped")
                .setQuery(matchAllQuery())
                .addAggregation(max("max").field("value"))
                .execute().actionGet();

        assertHitCount(searchResponse, 10);

        Max max = searchResponse.getAggregations().get("max");
        assertThat(max, notNullValue());
        assertThat(max.getName(), equalTo("max"));
        assertThat(max.getValue(), equalTo(10.0));
    }

    @Override
    public void testSingleValuedFieldWithValueScript() throws Exception {
        SearchResponse searchResponse = client().prepareSearch("idx")
                .setQuery(matchAllQuery())
                .addAggregation(
                        max("max")
                                .field("value")
                                .script(new Script(ScriptType.INLINE, AggregationTestScriptsPlugin.NAME, "_value + 1", emptyMap())))
                .execute().actionGet();

        assertHitCount(searchResponse, 10);

        Max max = searchResponse.getAggregations().get("max");
        assertThat(max, notNullValue());
        assertThat(max.getName(), equalTo("max"));
        assertThat(max.getValue(), equalTo(11.0));
    }

    @Override
    public void testSingleValuedFieldWithValueScriptWithParams() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("inc", 1);
        SearchResponse searchResponse = client().prepareSearch("idx")
                .setQuery(matchAllQuery())
                .addAggregation(
                        max("max")
                                .field("value")
                                .script(new Script(ScriptType.INLINE, AggregationTestScriptsPlugin.NAME, "_value + inc", params)))
                .get();

        assertHitCount(searchResponse, 10);

        Max max = searchResponse.getAggregations().get("max");
        assertThat(max, notNullValue());
        assertThat(max.getName(), equalTo("max"));
        assertThat(max.getValue(), equalTo(11.0));
    }

    @Override
    public void testMultiValuedField() throws Exception {
        SearchResponse searchResponse = client().prepareSearch("idx")
                .setQuery(matchAllQuery())
                .addAggregation(max("max").field("values"))
                .execute().actionGet();

        assertHitCount(searchResponse, 10);

        Max max = searchResponse.getAggregations().get("max");
        assertThat(max, notNullValue());
        assertThat(max.getName(), equalTo("max"));
        assertThat(max.getValue(), equalTo(12.0));
    }

    @Override
    public void testMultiValuedFieldWithValueScript() throws Exception {
        SearchResponse searchResponse = client().prepareSearch("idx")
                .setQuery(matchAllQuery())
                .addAggregation(
                        max("max")
                                .field("values")
                                .script(new Script(ScriptType.INLINE, AggregationTestScriptsPlugin.NAME, "_value + 1", emptyMap())))
                .get();

        assertHitCount(searchResponse, 10);

        Max max = searchResponse.getAggregations().get("max");
        assertThat(max, notNullValue());
        assertThat(max.getName(), equalTo("max"));
        assertThat(max.getValue(), equalTo(13.0));
    }

    @Override
    public void testMultiValuedFieldWithValueScriptWithParams() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("inc", 1);
        SearchResponse searchResponse = client().prepareSearch("idx")
                .setQuery(matchAllQuery())
                .addAggregation(
                        max("max")
                                .field("values")
                                .script(new Script(ScriptType.INLINE, AggregationTestScriptsPlugin.NAME, "_value + inc", params)))
                .get();

        assertHitCount(searchResponse, 10);

        Max max = searchResponse.getAggregations().get("max");
        assertThat(max, notNullValue());
        assertThat(max.getName(), equalTo("max"));
        assertThat(max.getValue(), equalTo(13.0));
    }

    @Override
    public void testScriptSingleValued() throws Exception {
        SearchResponse searchResponse = client().prepareSearch("idx")
                .setQuery(matchAllQuery())
                .addAggregation(
                        max("max")
                                .script(new Script(ScriptType.INLINE, AggregationTestScriptsPlugin.NAME, "doc['value'].value", emptyMap())))
                .execute().actionGet();

        assertHitCount(searchResponse, 10);

        Max max = searchResponse.getAggregations().get("max");
        assertThat(max, notNullValue());
        assertThat(max.getName(), equalTo("max"));
        assertThat(max.getValue(), equalTo(10.0));
    }

    @Override
    public void testScriptSingleValuedWithParams() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("inc", 1);

        Script script = new Script(ScriptType.INLINE, AggregationTestScriptsPlugin.NAME, "doc['value'].value + inc", params);

        SearchResponse searchResponse = client().prepareSearch("idx")
                .setQuery(matchAllQuery())
                .addAggregation(max("max").script(script))
                .get();

        assertHitCount(searchResponse, 10);

        Max max = searchResponse.getAggregations().get("max");
        assertThat(max, notNullValue());
        assertThat(max.getName(), equalTo("max"));
        assertThat(max.getValue(), equalTo(11.0));
    }

    @Override
    public void testScriptMultiValued() throws Exception {
        SearchResponse searchResponse = client().prepareSearch("idx")
                .setQuery(matchAllQuery())
                .addAggregation(
                        max("max")
                                .script(new Script(ScriptType.INLINE,
                                    AggregationTestScriptsPlugin.NAME, "doc['values'].values", Collections.emptyMap())))
                .get();

        assertHitCount(searchResponse, 10);

        Max max = searchResponse.getAggregations().get("max");
        assertThat(max, notNullValue());
        assertThat(max.getName(), equalTo("max"));
        assertThat(max.getValue(), equalTo(12.0));
    }

    @Override
    public void testScriptMultiValuedWithParams() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("inc", 1);

        Script script = new Script(ScriptType.INLINE, AggregationTestScriptsPlugin.NAME, "[ doc['value'].value, doc['value'].value + inc ]",
            params);

        SearchResponse searchResponse = client().prepareSearch("idx").setQuery(matchAllQuery())
                .addAggregation(max("max").script(script))
                .get();

        assertHitCount(searchResponse, 10);

        Max max = searchResponse.getAggregations().get("max");
        assertThat(max, notNullValue());
        assertThat(max.getName(), equalTo("max"));
        assertThat(max.getValue(), equalTo(11.0));
    }

    @Override
    public void testOrderByEmptyAggregation() throws Exception {
        SearchResponse searchResponse = client().prepareSearch("idx").setQuery(matchAllQuery())
                .addAggregation(terms("terms").field("value").order(BucketOrder.compound(BucketOrder.aggregation("filter>max", true)))
                        .subAggregation(filter("filter", termQuery("value", 100)).subAggregation(max("max").field("value"))))
                .get();

        assertHitCount(searchResponse, 10);

        Terms terms = searchResponse.getAggregations().get("terms");
        assertThat(terms, notNullValue());
        List<? extends Terms.Bucket> buckets = terms.getBuckets();
        assertThat(buckets, notNullValue());
        assertThat(buckets.size(), equalTo(10));

        for (int i = 0; i < 10; i++) {
            Terms.Bucket bucket = buckets.get(i);
            assertThat(bucket, notNullValue());
            assertThat(bucket.getKeyAsNumber(), equalTo((long) i + 1));
            assertThat(bucket.getDocCount(), equalTo(1L));
            Filter filter = bucket.getAggregations().get("filter");
            assertThat(filter, notNullValue());
            assertThat(filter.getDocCount(), equalTo(0L));
            Max max = filter.getAggregations().get("max");
            assertThat(max, notNullValue());
            assertThat(max.value(), equalTo(Double.NEGATIVE_INFINITY));

        }
    }

    /**
     * Make sure that a request using a script does not get cached and a request
     * not using a script does get cached.
     */
    public void testDontCacheScripts() throws Exception {
        assertAcked(prepareCreate("cache_test_idx").addMapping("type", "d", "type=long")
                .setSettings(Settings.builder().put("requests.cache.enable", true).put("number_of_shards", 1).put("number_of_replicas", 1))
                .get());
        indexRandom(true, client().prepareIndex("cache_test_idx", "type", "1").setSource("s", 1),
                client().prepareIndex("cache_test_idx", "type", "2").setSource("s", 2));

        // Make sure we are starting with a clear cache
        assertThat(client().admin().indices().prepareStats("cache_test_idx").setRequestCache(true).get().getTotal().getRequestCache()
                .getHitCount(), equalTo(0L));
        assertThat(client().admin().indices().prepareStats("cache_test_idx").setRequestCache(true).get().getTotal().getRequestCache()
                .getMissCount(), equalTo(0L));

        // Test that a request using a script does not get cached
        SearchResponse r = client().prepareSearch("cache_test_idx").setSize(0).addAggregation(
                max("foo").field("d").script(new Script(ScriptType.INLINE, AggregationTestScriptsPlugin.NAME, "_value + 1", emptyMap())))
                .get();
        assertSearchResponse(r);

        assertThat(client().admin().indices().prepareStats("cache_test_idx").setRequestCache(true).get().getTotal().getRequestCache()
                .getHitCount(), equalTo(0L));
        assertThat(client().admin().indices().prepareStats("cache_test_idx").setRequestCache(true).get().getTotal().getRequestCache()
                .getMissCount(), equalTo(0L));

        // To make sure that the cache is working test that a request not using
        // a script is cached
        r = client().prepareSearch("cache_test_idx").setSize(0).addAggregation(max("foo").field("d")).get();
        assertSearchResponse(r);

        assertThat(client().admin().indices().prepareStats("cache_test_idx").setRequestCache(true).get().getTotal().getRequestCache()
                .getHitCount(), equalTo(0L));
        assertThat(client().admin().indices().prepareStats("cache_test_idx").setRequestCache(true).get().getTotal().getRequestCache()
                .getMissCount(), equalTo(1L));
    }

    public void testEarlyTermination() throws Exception {
        SearchResponse searchResponse = client().prepareSearch("idx")
            .setTrackTotalHits(false)
            .setQuery(matchAllQuery())
            .addAggregation(max("max").field("values"))
            .addAggregation(count("count").field("values"))
            .execute().actionGet();

        Max max = searchResponse.getAggregations().get("max");
        assertThat(max, notNullValue());
        assertThat(max.getName(), equalTo("max"));
        assertThat(max.getValue(), equalTo(12.0));

        ValueCount count = searchResponse.getAggregations().get("count");
        assertThat(count.getName(), equalTo("count"));
        assertThat(count.getValue(), equalTo(20L));
    }
}
