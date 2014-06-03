/*
 * Copyright 2011 Corpuslinguistic working group Humboldt University Berlin.
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
package annis.gui.resultview;

import annis.CommonHelper;
import annis.libgui.PluginSystem;
import annis.gui.QueryController;
import annis.gui.SearchUI;
import annis.gui.components.OnLoadCallbackExtension;
import annis.gui.model.PagedResultQuery;
import annis.gui.paging.PagingComponent;
import annis.libgui.Helper;
import annis.libgui.InstanceConfig;
import static annis.gui.controlpanel.SearchOptionsPanel.KEY_DEFAULT_BASE_TEXT_SEGMENTATION;
import annis.libgui.ResolverProviderImpl;
import annis.model.AnnisConstants;
import annis.resolver.ResolverEntry;
import annis.resolver.SingleResolverRequest;
import annis.service.objects.CorpusConfig;
import com.google.common.base.Preconditions;
import com.vaadin.server.AbstractClientConnector;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.CssLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.MenuBar;
import com.vaadin.ui.MenuBar.MenuItem;
import com.vaadin.ui.Notification;
import com.vaadin.ui.Panel;
import com.vaadin.ui.ProgressBar;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.themes.ChameleonTheme;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCommon.SaltProject;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCommon.sCorpusStructure.SCorpusGraph;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCommon.sCorpusStructure.SDocument;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCommon.sDocumentStructure.SDocumentGraph;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCore.SFeature;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCore.SNode;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;

/**
 *
 * @author thomas
 */
public class ResultViewPanel extends VerticalLayout implements
  OnLoadCallbackExtension.Callback
{

  private static final org.slf4j.Logger log = LoggerFactory.getLogger(
    ResultViewPanel.class);

  public static final String NULL_SEGMENTATION_VALUE = "tokens (default)";

  private Map<HashSet<SingleResolverRequest>, List<ResolverEntry>> cacheResolver;

  public static final String FILESYSTEM_CACHE_RESULT
    = "ResultSetPanel_FILESYSTEM_CACHE_RESULT";

  public static final String MAPPING_HIDDEN_ANNOS = "hidden_annos";

  private PagingComponent paging;

  private ProgressBar progressResult;

  private PluginSystem ps;

  private MenuItem miTokAnnos;

  private MenuItem miSegmentation;

  private TreeMap<String, Boolean> tokenAnnoVisible;

  private QueryController controller;

  private String selectedSegmentationLayer;

  private Set<String> segmentationLayerSet
    = Collections.synchronizedSet(new TreeSet<String>());

  private Set<String> tokenAnnotationLevelSet
    = Collections.synchronizedSet(new TreeSet<String>());

  private InstanceConfig instanceConfig;

  private CssLayout resultLayout;

  private List<SingleResultPanel> resultPanelList;

  private String segmentationName;

  private int currentResults;

  private int numberOfResults;

  private transient BlockingQueue<SaltProject> projectQueue;
  
  private UUID queryId;

  private PagedResultQuery currentQuery;

  public ResultViewPanel(QueryController controller,
    PluginSystem ps, UUID queryId, InstanceConfig instanceConfig)
  {
    this.tokenAnnoVisible = new TreeMap<String, Boolean>();
    this.ps = ps;
    this.queryId = queryId;
    this.controller = controller;
    this.selectedSegmentationLayer = controller.getPreparedQuery().
      getSegmentation();

    cacheResolver
      = Collections.synchronizedMap(
        new HashMap<HashSet<SingleResolverRequest>, List<ResolverEntry>>());

    resultPanelList
      = Collections.synchronizedList(new LinkedList<SingleResultPanel>());

    resultLayout = new CssLayout();
    resultLayout.addStyleName("result-view-css");
    Panel resultPanel = new Panel(resultLayout);
    resultPanel.setSizeFull();
    resultPanel.addStyleName(ChameleonTheme.PANEL_BORDERLESS);

    this.instanceConfig = instanceConfig;

    setSizeFull();
    setMargin(false);

    MenuBar mbResult = new MenuBar();
    mbResult.setWidth("100%");
    mbResult.addStyleName("menu-hover");
    addComponent(mbResult);

    miSegmentation = mbResult.addItem("Base text", null);
    miTokAnnos = mbResult.addItem("Token Annotations", null);

    progressResult = new ProgressBar();

    progressResult.setVisible(false);

    addComponent(progressResult);
    addComponent(resultPanel);

    setComponentAlignment(progressResult, Alignment.MIDDLE_CENTER);

    setExpandRatio(mbResult, 0.0f);
    setExpandRatio(progressResult, 0.0f);
    setExpandRatio(resultPanel, 1.0f);

    paging = new PagingComponent();

    addComponent(paging, 1);

    setComponentAlignment(paging, Alignment.TOP_CENTER);
    setExpandRatio(paging, 0.0f);
  }

  /**
   * Informs the user about the searching process.
   *
   * @param query Represents a limited query
   */
  public void showMatchSearchInProgress(PagedResultQuery query)
  {
    resultLayout.removeAllComponents();

    progressResult.setIndeterminate(true);
    progressResult.setCaption("Searching for \"" + query.getQuery().replaceAll(
      "\n",
      " ") + "\"");
    progressResult.setVisible(true);
    setExpandRatio(progressResult, 1.0f);

    segmentationName = query.getSegmentation();
  }

  public void showNoResult()
  {
    resultLayout.removeAllComponents();
    currentResults = 0;

    progressResult.setCaption("");
    progressResult.setVisible(false);

    // nothing to show since we have an empty result
    Label lblNoResult = new Label("No matches found.");
    lblNoResult.setWidth("100%");
    lblNoResult.addStyleName("result-view-no-content");
    
    resultLayout.addComponent(lblNoResult);
  }

  public void showSubgraphSearchInProgress(PagedResultQuery q, float percent)
  {
    if (percent == 0.0f)
    {
      resultLayout.removeAllComponents();
      currentResults = 0;
    }

    progressResult.setIndeterminate(false);
    progressResult.setCaption("");
    progressResult.setVisible(true);
    setExpandRatio(progressResult, 0.0f);
    progressResult.setValue(percent);
  }

  /**
   * Set a new querys in result panel.
   *
   * @param queue holds the salt graph
   * @param q holds the ordinary query
   * @param numberOfResults the figure of all matches.
   */
  public void setQueryResultQueue(BlockingQueue<SaltProject> queue,
    PagedResultQuery q, int numberOfResults)
  {
    this.projectQueue = queue;
    this.currentQuery = q;
    this.numberOfResults = numberOfResults;

    paging.setPageSize(q.getLimit(), false);
    paging.setInfo(q.getQuery());

    resultLayout.removeAllComponents();
    resultPanelList.clear();

    Set<String> corpora = q.getCorpora();

    if (corpora.size() == 1)
    {

      // fetched corpus config
      CorpusConfig corpusConfig = Helper.getCorpusConfig(corpora.iterator().
        next());
      if (corpusConfig != null && corpusConfig.getConfig() != null
        && corpusConfig.getConfig().containsKey(
          KEY_DEFAULT_BASE_TEXT_SEGMENTATION))
      {
        if (selectedSegmentationLayer == null)
        {
          selectedSegmentationLayer = corpusConfig.getConfig(
            KEY_DEFAULT_BASE_TEXT_SEGMENTATION);
        }
      }
    }

    // get the first query result
    SaltProject first = queue.poll();
    Preconditions.checkState(first != null,
      "There must be already an element in the queue");

    addQueryResult(q, Arrays.asList(first));
  }

  private void resetQueryResultQueue()
  {
    this.projectQueue = null;
    this.currentQuery = null;
    this.currentResults = 0;
    this.numberOfResults = 0;
  }

  private void addQueryResult(PagedResultQuery q, List<SaltProject> subgraphList)
  {
    if (q == null)
    {
      return;
    }

    List<SingleResultPanel> newPanels = new LinkedList<SingleResultPanel>();
    try
    {
      if (subgraphList == null || subgraphList.isEmpty())
      {
        Notification.show("Could not get subgraphs",
          Notification.Type.TRAY_NOTIFICATION);
      }
      else
      {
        for(SaltProject p : subgraphList)
        {
          updateVariables(p);
          newPanels = createPanels(p, q.getOffset() + currentResults);
          currentResults += newPanels.size();

          progressResult.setValue(((float) currentResults) / (float) (numberOfResults));

          if (currentResults == numberOfResults)
          {
            resetQueryResultQueue();
          }

          for (SingleResultPanel panel : newPanels)
          {
            resultPanelList.add(panel);
            resultLayout.addComponent(panel);
            panel.setSegmentationLayer(selectedSegmentationLayer);
          }
        }
        
        if(currentResults == numberOfResults)
        {
          showFinishedSubgraphSearch();
        }
        

        if (projectQueue != null && !newPanels.isEmpty() && currentResults < numberOfResults)
        {
          // add a callback so we can load the next single result
          OnLoadCallbackExtension ext = new OnLoadCallbackExtension(this, 250);
          ext.extend(newPanels.get(newPanels.size() - 1));
        }
      }
    }
    catch (Throwable ex)
    {
      log.error(null, ex);
    }

  }

  public void showFinishedSubgraphSearch()
  {
    progressResult.setVisible(false);
  }

  private List<SingleResultPanel> createPanels(SaltProject p, int offset)
  {
    List<SingleResultPanel> result = new LinkedList<SingleResultPanel>();

    int i = 0;
    for (SCorpusGraph corpusGraph : p.getSCorpusGraphs())
    {
      SingleResultPanel panel = new SingleResultPanel(corpusGraph.
        getSDocuments().get(0),
        i + offset, new ResolverProviderImpl(cacheResolver), ps,
        getVisibleTokenAnnos(), segmentationName, queryId, controller, 
        instanceConfig);
      
      i++;

      panel.setWidth("100%");
      panel.setHeight("-1px");

      result.add(panel);
    }
    return result;
  }

  private void updateVariables(SaltProject p)
  {
    segmentationLayerSet.addAll(getSegmentationNames(p));
    tokenAnnotationLevelSet.addAll(CommonHelper.getTokenAnnotationLevelSet(p));
    Set<String> hiddenTokenAnnos = null;

    Set<String> corpusNames = CommonHelper.getToplevelCorpusNames(p);

    for (String corpusName : corpusNames)
    {

      CorpusConfig corpusConfig = Helper.getCorpusConfig(corpusName);

      if (corpusConfig != null && corpusConfig.containsKey(MAPPING_HIDDEN_ANNOS))
      {
        hiddenTokenAnnos = new HashSet<String>(
          Arrays.asList(
            StringUtils.split(
              corpusConfig.getConfig(MAPPING_HIDDEN_ANNOS), ",")
          )
        );
      }
    }

    if (hiddenTokenAnnos != null)
    {
      for (String tokenLevel : hiddenTokenAnnos)
      {
        if (tokenAnnotationLevelSet.contains(tokenLevel))
        {
          tokenAnnotationLevelSet.remove(tokenLevel);
        }
      }
    }

    updateSegmentationLayer(segmentationLayerSet);
    updateVisibleToken(tokenAnnotationLevelSet);
  }
  
  private Set<String> getSegmentationNames(SaltProject p)
  {
    Set<String> result = new TreeSet<String>();

    for (SCorpusGraph corpusGraphs : p.getSCorpusGraphs())
    {
      for (SDocument doc : corpusGraphs.getSDocuments())
      {
        SDocumentGraph g = doc.getSDocumentGraph();
        if (g != null)
        {
          // collect the start nodes of a segmentation chain of length 1
          for (SNode n : g.getSNodes())
          {
            SFeature feat
              = n.getSFeature(AnnisConstants.ANNIS_NS,
                AnnisConstants.FEAT_FIRST_NODE_SEGMENTATION_CHAIN);
            if (feat != null && feat.getSValueSTEXT() != null)
            {
              result.add(feat.getSValueSTEXT());
            }
          }
        } // end if graph not null
      }
    }
    
    return result;
  }

  public void setCount(int count)
  {
    paging.setCount(count, false);
    paging.setStartNumber(controller.getPreparedQuery().getOffset());
  }

  public SortedSet<String> getVisibleTokenAnnos()
  {
    TreeSet<String> result = new TreeSet<String>();

    for (Entry<String, Boolean> e : tokenAnnoVisible.entrySet())
    {
      if (e.getValue().booleanValue() == true)
      {
        result.add(e.getKey());
      }
    }

    return result;
  }

  /**
   * Listens to events on the base text menu and updates the segmentation layer.
   */
  private class MenuBaseTextCommand implements MenuBar.Command
  {

    @Override
    public void menuSelected(MenuItem selectedItem)
    {
      // remember old value
      String oldSegmentationLayer = selectedSegmentationLayer;
      
      // set the new selected item
      selectedSegmentationLayer = selectedItem.getText();

      if (NULL_SEGMENTATION_VALUE.equals(selectedSegmentationLayer))
      {
        selectedSegmentationLayer = null;
      }
      for (MenuItem mi : miSegmentation.getChildren())
      {
        mi.setChecked(mi == selectedItem);
      }
      
      if(oldSegmentationLayer != null)
      {
        if(!oldSegmentationLayer.equals(selectedSegmentationLayer))
        {
          setSegmentationLayer(selectedSegmentationLayer);
        }
      }
      else if(selectedSegmentationLayer != null)
      {
        // oldSegmentation is null, but selected is not
        setSegmentationLayer(selectedSegmentationLayer);
      }
      
      if(selectedSegmentationLayer != null)
      {
        
        //update URL with newly selected segmentation layer
        PagedResultQuery q;
        
        SearchUI sui = (SearchUI) UI.getCurrent();
        q = sui.getQueryController().getPreparedQuery();        
        q.setSegmentation(selectedSegmentationLayer);
                       
        sui.updateFragment(q);
    	  
      }      
    }
  }

  private void updateSegmentationLayer(Set<String> segLayers)
  {

    // clear the menu base text
    miSegmentation.removeChildren();

    // add the default token layer
    segLayers.add("");

    // iterate of all segmentation layers and add them to the menu
    for (String s : segLayers)
    {
      // the new menu entry
      MenuItem miSingleSegLayer;

      /**
       * TODO maybe it would be better, to mark the default text level
       * corresponding to the corpus.properties.
       *
       * There exists always a default text level.
       */
      if (s == null || "".equals(s))
      {
        miSingleSegLayer = miSegmentation.addItem(
          NULL_SEGMENTATION_VALUE, new MenuBaseTextCommand());
      }
      else
      {
        miSingleSegLayer = miSegmentation.addItem(s, new MenuBaseTextCommand());
      }

      // mark as selectable
      miSingleSegLayer.setCheckable(true);

      /**
       * Check if a segmentation item must set checked. If no segmentation layer
       * is selected, set the default layer as selected.
       */
      if ((selectedSegmentationLayer == null && "".equals(s))
        || s.equals(selectedSegmentationLayer))
      {
        miSingleSegLayer.setChecked(true);
      }
      else
      {
        miSingleSegLayer.setChecked(false);
      }
    } // end iterate for segmentation layer
  }

  public void updateVisibleToken(Set<String> tokenAnnotationLevelSet)
  {
    // if no token annotations are there, do not show this mneu
    if (tokenAnnotationLevelSet == null
      || tokenAnnotationLevelSet.isEmpty())
    {
      miTokAnnos.setVisible(false);
    }
    else
    {
      miTokAnnos.setVisible(true);
    }

    // add new annotations
    if(tokenAnnotationLevelSet != null)
    {
      for (String s : tokenAnnotationLevelSet)
      {
        if (!tokenAnnoVisible.containsKey(s))
        {
          tokenAnnoVisible.put(s, Boolean.TRUE);
        }
      }
    }

    miTokAnnos.removeChildren();

    if (tokenAnnotationLevelSet != null)
    {
      for (final String a : tokenAnnotationLevelSet)
      {
        MenuItem miSingleTokAnno = miTokAnnos.addItem(a.replaceFirst("::", ":"), new MenuBar.Command()
        {
          @Override
          public void menuSelected(MenuItem selectedItem)
          {

            if (selectedItem.isChecked())
            {
              tokenAnnoVisible.put(a, Boolean.TRUE);
            }
            else
            {
              tokenAnnoVisible.put(a, Boolean.FALSE);
            }

            setVisibleTokenAnnosVisible(getVisibleTokenAnnos());
          }
        });

        miSingleTokAnno.setCheckable(true);
        miSingleTokAnno.setChecked(tokenAnnoVisible.get(a).booleanValue());
      }
    }
  }

  @Override
  public boolean onCompononentLoaded(AbstractClientConnector source)
  {
    if (source != null && projectQueue != null && currentQuery != null)
    {
      LinkedList<SaltProject> subgraphs = new LinkedList<SaltProject>();
      SaltProject p;
      while((p = projectQueue.poll()) != null)
      {
        subgraphs.add(p);
      }
      if (subgraphs.isEmpty())
      {
        log.debug("no SaltProject graph in queue");
        return false;
      }
      log.debug("adding new SaltProject graph");
      addQueryResult(currentQuery, subgraphs);
      return true;


    }

    return true;
  }

  private void setVisibleTokenAnnosVisible(SortedSet<String> annos)
  {
    for (SingleResultPanel p : resultPanelList)
    {
      p.setVisibleTokenAnnosVisible(annos);
    }
  }

  private void setSegmentationLayer(String segmentationLayer)
  {
    for (SingleResultPanel p : resultPanelList)
    {
      p.setSegmentationLayer(segmentationLayer);
    }
  }

  public PagingComponent getPaging()
  {
    return paging;
  }
}
