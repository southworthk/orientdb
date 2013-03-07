/*
 * Copyright 2010-2012 Luca Garulli (l.garulli--at--orientechnologies.com)
 * Copyright 2013 Geomatys.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.orientechnologies.orient.core.sql.command;

import com.orientechnologies.orient.core.command.OCommandContext;
import java.util.Map;

import com.orientechnologies.orient.core.command.OCommandListener;
import com.orientechnologies.orient.core.command.OCommandRequest;
import com.orientechnologies.orient.core.db.graph.OGraphDatabase;
import com.orientechnologies.orient.core.db.record.ODatabaseRecord;
import com.orientechnologies.orient.core.db.record.ODatabaseRecordTx;
import com.orientechnologies.orient.core.db.record.OIdentifiable;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.metadata.security.ODatabaseSecurityResources;
import com.orientechnologies.orient.core.metadata.security.ORole;
import com.orientechnologies.orient.core.record.ORecordAbstract;
import com.orientechnologies.orient.core.sql.OCommandSQLParsingException;
import com.orientechnologies.orient.core.sql.model.OExpression;
import com.orientechnologies.orient.core.sql.model.OName;
import com.orientechnologies.orient.core.sql.model.OQuerySource;
import com.orientechnologies.orient.core.sql.parser.OSQLParser;
import static com.orientechnologies.orient.core.sql.parser.SQLGrammarUtils.*;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * SQL DELETE EDGE command.
 * 
 * @author Luca Garulli
 * @author Johann Sorel (Geomatys)
 */
public class OCommandDeleteEdge extends OCommandAbstract implements OCommandListener {
  
  public static final String NAME = "DELETE EDGE";
  
  private ORID single;
  private ORID from;
  private ORID to;
  private OSQLParser.SourceContext source;
  private OSQLParser.FilterContext filter;
  private long recordCount = 0;

  public OCommandDeleteEdge() {
  }

  @Override
  protected OGraphDatabase getDatabase() {
      ODatabaseRecord db = super.getDatabase();
      if (!(db instanceof OGraphDatabase))
      db = new OGraphDatabase((ODatabaseRecordTx) db);
      return (OGraphDatabase) db;
  }

  public OCommandDeleteEdge parse(final OCommandRequest iRequest) throws OCommandSQLParsingException {    
    final ODatabaseRecord database = getDatabase();
    database.checkSecurity(ODatabaseSecurityResources.COMMAND, ORole.PERMISSION_READ);

    final OSQLParser.CommandDeleteEdgeContext candidate = getCommand(iRequest, OSQLParser.CommandDeleteEdgeContext.class);
    
    if(candidate.deleteEdgeFrom()!= null){
      from = (ORID)visit(candidate.deleteEdgeFrom().orid()).evaluate(null, null);
    }    
    if(candidate.deleteEdgeTo()!= null){
      to = (ORID)visit(candidate.deleteEdgeTo().orid()).evaluate(null, null);
    }
    
    if(candidate.source()!= null){
      source = candidate.source();
      if(source.orid() != null){
        single = (ORID)visit(source.orid()).evaluate(null, null);
      }else if(source.expression() != null){
        //ok
      }else{
        //complex source
        throw new OCommandSQLParsingException("Source must be simple : orid or class");
      }
    }
    filter = candidate.filter();
        
    return this;
  }

  @Override
  public Object execute(Map<Object, Object> iArgs) {
    OGraphDatabase db = getDatabase();
    if(single != null){
      // REMOVE PUNCTUAL RID
      if (db.removeEdge(single)){
        recordCount++;
      }
    }else if(source != null){
      final OCommandSelect subselect = new OCommandSelect();
      subselect.parse(source, filter);
      subselect.addListener(this);
      subselect.execute(iArgs);
    }else if(source == null && from == null && to == null){
      final OCommandSelect subselect = new OCommandSelect();
      subselect.parse(source, filter);
      final OQuerySource src = new OQuerySource();
      src.setTargetClasse("E");
      subselect.setSource(src);
      subselect.addListener(this);
      subselect.execute(iArgs);
    }else{
      final Set<OIdentifiable> edges;
      
      if(from != null && to != null){
        // REMOVE ALL THE EDGES BETWEEN VERTICES
        edges = db.getEdgesBetweenVertexes(from, to);
      }else if(from != null){
        // REMOVE ALL THE EDGES THAT START FROM A VERTEXES
        edges = new HashSet<OIdentifiable>(db.getOutEdges(from));
      }else{
        // REMOVE ALL THE EDGES THAT ARRIVE TO A VERTEXES
        edges = new HashSet<OIdentifiable>(db.getInEdges(to));
      }
      
      if(filter != null){
        final OCommandContext ctx = getContext();
        final OExpression f = visit(filter);
        for (Iterator<OIdentifiable> it = edges.iterator(); it.hasNext();) {
          final OIdentifiable edge = it.next();
          final Object valid = f.evaluate(ctx, edge);
          if (!Boolean.TRUE.equals(valid)) {
            it.remove();
          }
        }
      }
      
      // DELETE THE FOUND EDGES
      recordCount = edges.size();
      for (OIdentifiable edge : edges){
        db.removeEdge(edge);
      }
    }
    
    return recordCount;
  }
  
  @Override
  public String getSyntax() {
    return "DELETE EDGE <rid>|FROM <rid>|TO <rid>|<[<class>] [WHERE <conditions>]>";
  }
  
  
  //////////////////////////////////////////////////////////////////////////////
  //Sub select events //////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////
  
  @Override
  public boolean result(Object iRecord) {
    final ORecordAbstract<?> record = (ORecordAbstract<?>) iRecord;

    final ORID id = record.getIdentity();
    if (id.isValid()) {
      if (((OGraphDatabase) getDatabase()).removeEdge(id)) {
        recordCount++;
      }
    }
    return true;
  }

  @Override
  public void end() {
  }

  @Override
  public void onBegin(Object iTask, long iTotal) {
  }

  @Override
  public boolean onProgress(Object iTask, long iCounter, float iPercent) {
    return true;
  }

  @Override
  public void onCompletition(Object iTask, boolean iSucceed) {
  }
  
}
