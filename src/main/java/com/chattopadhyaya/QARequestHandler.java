package com.chattopadhyaya;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.handler.StandardRequestHandler;
import org.apache.solr.handler.component.SearchHandler;
import org.apache.solr.request.LocalSolrQueryRequest;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.request.SolrRequestInfo;
import org.apache.solr.response.SolrQueryResponse;

public class QARequestHandler extends SearchHandler {

  List<QAPattern> patterns = new ArrayList<QARequestHandler.QAPattern>();

  @Override
  public void init(NamedList args) {
    super.init(args);
    initPatterns();
    System.out.println("Putting patterns into the list from the init()");
  }

  public static String VERSION = "1.0";
  @Override
  public void handleRequestBody(SolrQueryRequest req, SolrQueryResponse rsp) throws Exception {

    NamedList nl = new NamedList();
    nl.add("version", VERSION);
    rsp.addResponseHeader(nl);

    if(patterns.size()==0) {
      initPatterns();
    }

    System.out.println("CORE: "+req.getCore());
    System.out.println("RHs: "+req.getCore().getRequestHandlers());
    SolrParams params = req.getOriginalParams();
    ModifiableSolrParams mparams = new ModifiableSolrParams(params);

    String question = params.get("q");
    System.out.println("QUESTION: "+question);
    if(question==null)
      return;

    QAPattern matchedPattern = null;
    
    for(QAPattern pat: patterns) {
      if(pat.doesMatch(question)) {
        matchedPattern = pat;
        
        String q = pat.getQuery(question);
        String fl = "id,name_t,"+pat.returnField;

        System.out.println("Found pattern: "+q+", "+fl);
        mparams.set("q", q);
        mparams.set("fl", fl);
        mparams.set("PluginVersion", VERSION);
        mparams.set("MatchedPattern", matchedPattern.toString());

        forward(req, "/select", mparams, rsp);
        return;
      }
    }
    
    // if nothing works, fall back to textual search
    mparams.set("q", "description_t:'"+question+"'", "fl", "*");
    mparams.set("PluginVersion", VERSION);
    mparams.set("MatchedPattern", matchedPattern.toString());
    forward(req, "/select", mparams, rsp);
  }

  @Override
  public String getDescription() {
    return "Question answering query parser, accepts the question with 'q' parameter";
  }


  class QAPattern {
    Pattern pattern;
    int entityGroup; 
    String query; 
    String returnField;

    public QAPattern(String regex, int entityGroup, String queryPattern, String returnField) {
      this.entityGroup = entityGroup;
      this.query = queryPattern;
      this.returnField = returnField;
      this.pattern = Pattern.compile(regex);
    }

    String getQuery(String input) {
      String entity = extractEntity(input);
      if(entity==null)
        return null;
      return query.replaceAll("ENTITY", entity);
    }

    String extractEntity(String input) {
      Matcher mat = pattern.matcher(input);
      if(mat.find())
        return mat.group(entityGroup);
      return null;
    }

    boolean doesMatch(String input) {
      Matcher mat = pattern.matcher(input);
      return mat.matches();
    }
    
    @Override
    public String toString() {
      return pattern.pattern().toString();
    }
  }

  private static void forward(SolrQueryRequest req, String handler ,SolrParams params, SolrQueryResponse rsp){
    LocalSolrQueryRequest r = new LocalSolrQueryRequest(req.getCore(), params);
    SolrRequestInfo.getRequestInfo().addCloseHook( r );  // Close as late as possible...
    req.getCore().getRequestHandler(handler).handleRequest(r, rsp);
  }

  public void initPatterns() {
    // Date of birth patterns
    patterns.add(new QAPattern("(when was )(.*)( born)", 2, "name_t:'ENTITY' AND type_s:person", "dob_dt"));
    patterns.add(new QAPattern("(what is the birthdate of )(.*)", 2, "name_t:'ENTITY' AND type_s:person", "dob_dt"));
    patterns.add(new QAPattern("(birthday of )(.*)", 2, "name_t:'ENTITY' AND type_s:person", "dob_dt"));
    patterns.add(new QAPattern("(how old is )(.*)", 2, "name_t:'ENTITY' AND type_s:person", "dob_dt"));
    patterns.add(new QAPattern("(what is the age of )(.*)", 2, "name_t:'ENTITY' AND type_s:person", "dob_dt"));

    // Location/hometown patterns
    patterns.add(new QAPattern("(where does )(.*)(live)", 2, "name_t:'ENTITY' AND type_s:person", "hometown_s"));
    patterns.add(new QAPattern("(hometown of )(.*)", 2, "name_t:'ENTITY' AND type_s:person", "hometown_s"));

    // Capital of a country
    patterns.add(new QAPattern("(capital of )(.*)", 2, "name_t:'ENTITY' AND type_s:country", "capital_s"));
    patterns.add(new QAPattern("(what is capital of )(.*)", 2, "name_t:'ENTITY' AND type_s:country", "capital_s"));
    patterns.add(new QAPattern("(what is the capital of )(.*)", 2, "name_t:'ENTITY' AND type_s:country", "capital_s"));

  }
}
