/*
 * Copyright 2014 Corpuslinguistic working group Humboldt University Berlin.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package annis.gui.admin.controller;

import annis.gui.CriticalServiceQueryException;
import annis.gui.ServiceQueryException;
import annis.gui.admin.model.CorpusManagement;
import annis.gui.admin.view.CorpusListView;
import annis.gui.admin.view.UIView;
import annis.service.objects.AnnisCorpus;
import com.google.common.base.Joiner;
import com.sun.jersey.api.client.WebResource;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Thomas Krause <krauseto@hu-berlin.de>
 */
public class CorpusController
  implements CorpusListView.Listener, UIView.Listener
{
  
  private final CorpusManagement model;
  private final CorpusListView view;
  private final UIView uiView;

  public CorpusController(CorpusManagement model,
    CorpusListView view, UIView uiView)
  {
    this.model = model;
    this.view = view;    
    this.uiView = uiView;
    view.addListener(CorpusController.this);
    uiView.addListener(CorpusController.this);
  }
  
  private void fetchFromService()
  {
    try
    {
      model.fetchFromService();
      view.setAvailableCorpora(model.getCorpora());
    }
    catch(CriticalServiceQueryException ex)
    {
      uiView.showWarning("Cannot get the corpus list", null);
      view.setAvailableCorpora(new LinkedList<AnnisCorpus>());
    }
    catch(ServiceQueryException ex)
    {
      uiView.showInfo("Cannot get the corpus list", null);
      view.setAvailableCorpora(new LinkedList<AnnisCorpus>());
    }
  }

  @Override
  public void loginChanged(WebResource annisRootResource)
  {
    model.setRootResource(annisRootResource);
    fetchFromService();
  }

  @Override
  public void selectedTabChanged(Object selectedTab)
  {
    if(selectedTab == view)
    {
      fetchFromService();
    }
  }

  @Override
  public void deleteCorpora(Set<String> corpusName)
  {
    Set<String> deleted = new LinkedHashSet<>();
    for(String c : corpusName)
    {
      try
      {
        model.delete(c);
        deleted.add(c);
      }
      catch (CriticalServiceQueryException ex)
      {
        uiView.showWarning(ex.getMessage(), ex.getDescription());
      }
      catch (ServiceQueryException ex)
      {
        uiView.showInfo(ex.getMessage(), ex.getDescription());
      }
    }
    if(!deleted.isEmpty())
    {
      uiView.showInfo("Deleted corpora: " + Joiner.on(", ").join(deleted), null);
    }
    view.setAvailableCorpora(model.getCorpora());
  }
  
  
}
