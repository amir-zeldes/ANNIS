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
package annis.gui.controlpanel;

import annis.gui.AnnisUI;
import annis.gui.components.HelpButton;
import static annis.gui.controlpanel.SearchOptionsPanel.NULL_SEGMENTATION_VALUE;
import annis.gui.objects.QueryUIState;
import annis.libgui.Background;
import annis.libgui.Helper;
import annis.service.objects.CorpusConfig;
import annis.service.objects.CorpusConfigMap;
import annis.service.objects.OrderType;
import annis.service.objects.SegmentationList;
import com.google.common.collect.ImmutableList;
import com.google.common.escape.Escaper;
import com.google.common.net.UrlEscapers;
import com.google.gwt.thirdparty.guava.common.collect.Lists;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import com.vaadin.data.util.BeanItemContainer;
import com.vaadin.ui.AbstractSelect;
import com.vaadin.ui.ComboBox;
import com.vaadin.ui.FormLayout;
import com.vaadin.ui.Notification;
import com.vaadin.ui.ProgressBar;
import com.vaadin.ui.UI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author thomas
 * @author Benjamin Weißenfels <b.pixeldrama@gmail.com>
 */
public class SearchOptionsPanel extends FormLayout
{

  public static final String NULL_SEGMENTATION_VALUE = "tokens (default)";

  public static final String KEY_DEFAULT_CONTEXT_SEGMENTATION = "default-context-segmentation";

  public static final String KEY_DEFAULT_BASE_TEXT_SEGMENTATION = "default-base-text-segmentation";

  public static final String KEY_MAX_CONTEXT_LEFT = "max-context-left";

  public static final String KEY_MAX_CONTEXT_RIGHT = "max-context-right";

  public static final String KEY_CONTEXT_STEPS = "context-steps";

  public static final String KEY_DEFAULT_CONTEXT = "default-context";

  public static final String KEY_RESULT_PER_PAGE = "results-per-page";

  public static final String DEFAULT_CONFIG = "default-config";

  private static final Logger log = LoggerFactory.getLogger(
    SearchOptionsPanel.class);

  private final static Escaper urlPathEscape = UrlEscapers.
    urlPathSegmentEscaper();

  /**
   * Holds all available corpus configuarations, including the defautl
   * configeruation.
   *
   * The default configuration is available with the key "default-config"
   */
  private CorpusConfigMap corpusConfigurations;

  private final ComboBox cbLeftContext;

  private final ComboBox cbRightContext;

  private final ComboBox cbResultsPerPage;

  private final ComboBox cbSegmentation;
  private final HelpButton segmentationHelp;

  private final ComboBox cbOrder;

  // TODO: make this configurable
  private static final List<Integer> PREDEFINED_PAGE_SIZES = ImmutableList.of(
    1, 2, 5, 10, 20, 25
  );

  public static final List<Integer> PREDEFINED_CONTEXTS = ImmutableList.of(
    0, 1, 2, 5, 10, 20
  );

  private boolean optionsManuallyChanged = false;
  
  private final ProgressBar pbLoadConfig;

  /**
   * Caches all calculated corpus configurations. Note, also multiple selection
   * are stored. The keys for this kind of selection are generated by
   * {@link #buildKey()}.
   */
  private Map<String, CorpusConfig> lastSelection;

  public SearchOptionsPanel()
  {
    setWidth("100%");
    setHeight("-1px");
    
    // init the config cache
    lastSelection = new HashMap<>();
    
    pbLoadConfig = new ProgressBar();
    pbLoadConfig.setIndeterminate(true);
    pbLoadConfig.setCaption("Loading search options...");
    addComponent(pbLoadConfig);
    
    cbLeftContext = new ComboBox("Left Context");
    cbRightContext = new ComboBox("Right Context");
    cbResultsPerPage = new ComboBox("Results Per Page");

    cbLeftContext.setNullSelectionAllowed(false);
    cbRightContext.setNullSelectionAllowed(false);
    cbResultsPerPage.setNullSelectionAllowed(false);
//
    cbLeftContext.setNewItemsAllowed(true);
    cbRightContext.setNewItemsAllowed(true);
    cbResultsPerPage.setNewItemsAllowed(true);
//
    cbLeftContext.setTextInputAllowed(true);
    cbRightContext.setTextInputAllowed(true);
    cbResultsPerPage.setTextInputAllowed(true);

    cbLeftContext.setImmediate(true);
    cbRightContext.setImmediate(true);
    cbResultsPerPage.setImmediate(true);

//    cbLeftContext.addValidator(new IntegerRangeValidator("must be a number",
//      Integer.MIN_VALUE, Integer.MAX_VALUE));
//    cbRightContext.addValidator(new IntegerRangeValidator("must be a number",
//      Integer.MIN_VALUE, Integer.MAX_VALUE));
//    cbResultsPerPage.addValidator(new IntegerRangeValidator("must be a number",
//      Integer.MIN_VALUE, Integer.MAX_VALUE));
    cbSegmentation = new ComboBox("Show context in");

    cbSegmentation.setTextInputAllowed(
      false);
    cbSegmentation.setNullSelectionAllowed(
      true);

    cbSegmentation.setDescription(
      "If corpora with multiple "
      + "context definitions are selected, a list of available context units will be "
      + "displayed. By default context is calculated in ‘tokens’ "
      + "(e.g. 5 minimal units to the left and right of a search result). "
      + "Some corpora might offer further context definitions, e.g. in "
      + "syllables, word forms belonging to different speakers, normalized or "
      + "diplomatic segmentations of a manuscript, etc.");
    
    segmentationHelp = new HelpButton(cbSegmentation);

    cbOrder = new ComboBox("Order");
    cbOrder.setNewItemsAllowed(false);
    cbOrder.setNullSelectionAllowed(false);
    cbOrder.setImmediate(true);

    cbLeftContext.setVisible(false);
    cbRightContext.setVisible(false);
    cbResultsPerPage.setVisible(false);
    cbOrder.setVisible(false);
    segmentationHelp.setVisible(false);
    
    
    addComponent(cbLeftContext);
    addComponent(cbRightContext);
    
    addComponent(segmentationHelp);
    addComponent(cbResultsPerPage);
    addComponent(cbOrder);

  }

  @Override
  public void attach()
  {
    super.attach();
    
    cbSegmentation.setNullSelectionItemId(NULL_SEGMENTATION_VALUE);
    cbSegmentation.addItem(NULL_SEGMENTATION_VALUE);
    
    Background.run(new CorpusConfigUpdater(getUI()));

    if (getUI() instanceof AnnisUI)
    {
      AnnisUI ui = (AnnisUI) getUI();
     
      QueryUIState state = ui.getQueryState();
      
      cbLeftContext.setPropertyDataSource(state.getLeftContext());
      cbRightContext.setPropertyDataSource(state.getRightContext());
      cbResultsPerPage.setPropertyDataSource(state.getLimit());
      cbSegmentation.setPropertyDataSource(state.getContextBaseText());
      
      BeanItemContainer<OrderType> orderContainer
        = new BeanItemContainer<>(OrderType.class,
          Lists.newArrayList(OrderType.values()));
      
      // Unset the property data source first, otherwise the setting of 
      // the container data source will set the property value to null 
      cbOrder.setPropertyDataSource(null);
      cbOrder.setContainerDataSource(orderContainer);
      
      cbOrder.setPropertyDataSource(state.getOrder());
      
    }
  }

  public void updateSearchPanelConfigurationInBackground(
    final Set<String> corpora, final UI ui)
  {
    Background.run(new Runnable()
    {
      @Override
      public void run()
      {
        final List<String> segNames = getSegmentationNamesFromService(corpora);

        ui.access(new Runnable()
        {
          @Override
          public void run()
          {
            // check if a configuration is already calculated
            String key = buildKey(corpora);
            if (!lastSelection.containsKey(key))
            {
              lastSelection.put(key, generateConfig(corpora));
            }

            // get values from configuration
            Integer maxLeftCtx = Integer.parseInt(lastSelection.get(key).
              getConfig(
                KEY_MAX_CONTEXT_LEFT));
            Integer maxRightCtx = Integer.parseInt(lastSelection.get(key).
              getConfig(
                KEY_MAX_CONTEXT_RIGHT));
            Integer defaultCtx = Integer.parseInt(lastSelection.get(key).
              getConfig(
                KEY_DEFAULT_CONTEXT));
            Integer ctxSteps = Integer.parseInt(lastSelection.get(key).
              getConfig(
                KEY_CONTEXT_STEPS));
            Integer resultsPerPage = Integer.parseInt(lastSelection.get(key).
              getConfig(
                KEY_RESULT_PER_PAGE));
            String segment = lastSelection.get(key).getConfig(
              KEY_DEFAULT_CONTEXT_SEGMENTATION);

            int selectedLeftCtx = defaultCtx;
            int selectedRightCtx = defaultCtx;
            if (optionsManuallyChanged)
            {
              // check if we can re-use the old values
              Integer oldValueLeft = (Integer) cbLeftContext.getValue();
              Integer oldValueRight = (Integer) cbRightContext.getValue();

              if (oldValueLeft != null && oldValueLeft >= 0 && oldValueLeft <= maxLeftCtx)
              {
                selectedLeftCtx = oldValueLeft;
              }

              if (oldValueRight != null && oldValueRight >= 0 && oldValueRight <= maxRightCtx)
              {
                selectedRightCtx = oldValueRight;
              }
              String oldSegment = (String) cbSegmentation.getValue();
              if (oldSegment == null || segNames.contains(oldSegment))
              {
                segment = oldSegment;
              }
              Integer oldResultsPerPage = (Integer) cbResultsPerPage.getValue();
              if (oldResultsPerPage != null)
              {
                resultsPerPage = oldResultsPerPage;
              }

              // require another explicit manual change if values should be 
              // re-used on next corpus selection change
              optionsManuallyChanged = false;
            }

            // update the left and right context
            updateContext(cbLeftContext, maxLeftCtx, ctxSteps, selectedLeftCtx,
              false);
            updateContext(cbRightContext, maxRightCtx, ctxSteps,
              selectedRightCtx, false);
            updateResultsPerPage(resultsPerPage, false);

            updateSegmentations(segment, segNames);

          }
        });
      }
    });

  }

  private static List<String> getSegmentationNamesFromService(
    Set<String> corpora)
  {
    List<String> segNames = new ArrayList<>();
    WebResource service = Helper.getAnnisWebResource();
    if (service != null)
    {
      for (String corpus : corpora)
      {
        try
        {
          SegmentationList segList
            = service.path("query").path("corpora").path(urlPathEscape.escape(
                corpus))
            .path("segmentation-names")
            .get(SegmentationList.class);
          segNames.addAll(segList.getSegmentatioNames());
        }
        catch (UniformInterfaceException ex)
        {
          if (ex.getResponse().getStatus() == 403)
          {
            log.debug(
              "Did not have access rights to query segmentation names for corpus",
              ex);
          }
          else
          {
            log.warn("Could not query segmentation names for corpus", ex);
          }
        }
      }

    }

    return segNames;
  }

  private void updateSegmentations(String segment,
    List<String> segNames)
  {
    
    cbSegmentation.removeAllItems();
    cbSegmentation.setNullSelectionItemId(NULL_SEGMENTATION_VALUE);
    cbSegmentation.addItem(NULL_SEGMENTATION_VALUE);

    if ("tok".equalsIgnoreCase(segment))
    {
      cbSegmentation.setValue(NULL_SEGMENTATION_VALUE);
    }
    else if (segment != null)
    {
      cbSegmentation.addItem(segment);
      cbSegmentation.setValue(segment);
    }

    if (segNames != null && !segNames.isEmpty())
    {
      for (String s : segNames)
      {
        if (!s.equalsIgnoreCase(segment))
        {
          cbSegmentation.addItem(s);
        }
      }
    }
  }

  /**
   * If all values of a specific corpus property have the same value, this value
   * is returned, otherwise the value from the default config is choosen.
   *
   * @param key The property key.
   * @param corpora Specifies the selected corpora.
   * @return A value defined in the copurs.properties file or in the
   * admin-service.properties
   */
  private String theGreatestCommonDenominator(String key, Set<String> corpora)
  {
    int value = -1;

    for (String corpus : corpora)
    {
      CorpusConfig c = null;
      try
      {
        if (corpus.equals(Helper.DEFAULT_CONFIG))
        {
          continue;
        }

        if (corpusConfigurations.get(corpus) == null)
        {
          c = corpusConfigurations.get(DEFAULT_CONFIG);
        }
        else
        {
          c = corpusConfigurations.get(corpus);
        }

        // do nothing if not even default config is set
        if (c == null)
        {
          continue;
        }

        if (!c.getConfig().containsKey(key))
        {
          value = Integer.parseInt(
            corpusConfigurations.get(Helper.DEFAULT_CONFIG).getConfig().
            getProperty(key));
          break;
        }

        int tmp = Integer.parseInt(c.getConfig().getProperty(key));
        if (value < 0)
        {
          value = tmp;
        }

        if (value != tmp)
        {
          value = Integer.parseInt(
            corpusConfigurations.get(Helper.DEFAULT_CONFIG).getConfig().
            getProperty(key));
        }
      }
      catch (NumberFormatException ex)
      {
        log.error(
          "Cannot parse the string to an integer for key {} in corpus {} config",
          key, corpus, ex);
      }
    }

    return String.valueOf(value);
  }

  /**
   * Builds a config for selection of one or muliple corpora.
   *
   * @param corpora Specifies the combination of corpora, for which the config
   * is calculated.
   * @return A new config which takes into account the segementation of all
   * selected corpora.
   */
  private CorpusConfig generateConfig(Set<String> corpora)
  {
    corpusConfigurations = Helper.getCorpusConfigs();
    CorpusConfig corpusConfig = new CorpusConfig();

    // calculate the left and right context.
    String leftCtx = theGreatestCommonDenominator(KEY_MAX_CONTEXT_LEFT, corpora);
    String rightCtx = theGreatestCommonDenominator(KEY_MAX_CONTEXT_RIGHT,
      corpora);
    corpusConfig.setConfig(KEY_MAX_CONTEXT_LEFT, leftCtx);
    corpusConfig.setConfig(KEY_MAX_CONTEXT_RIGHT, rightCtx);

    // calculate the default-context
    corpusConfig.setConfig(KEY_CONTEXT_STEPS, theGreatestCommonDenominator(
      KEY_CONTEXT_STEPS, corpora));
    corpusConfig.setConfig(KEY_DEFAULT_CONTEXT, theGreatestCommonDenominator(
      KEY_DEFAULT_CONTEXT, corpora));

    // get the results per page
    corpusConfig.setConfig(KEY_RESULT_PER_PAGE, theGreatestCommonDenominator(
      KEY_RESULT_PER_PAGE, corpora));

    corpusConfig.setConfig(KEY_DEFAULT_CONTEXT_SEGMENTATION, checkSegments(
      KEY_DEFAULT_CONTEXT_SEGMENTATION, corpora));

    corpusConfig.setConfig(KEY_DEFAULT_BASE_TEXT_SEGMENTATION, checkSegments(
      KEY_DEFAULT_BASE_TEXT_SEGMENTATION, corpora));

    return corpusConfig;
  }

  /**
   * Checks, if all selected corpora have the same default segmentation layer.
   * If not the tok layer is taken, because every corpus has this one.
   *
   * @param key the key for the segementation config, must be
   * {@link #KEY_DEFAULT_BASE_TEXT_SEGMENTATION} or
   * {@link #KEY_DEFAULT_CONTEXT_SEGMENTATION}.
   * @param corpora the corpora which has to be checked.
   * @return "tok" or a segment which is defined in all corpora.
   */
  private String checkSegments(String key, Set<String> corpora)
  {
    String segmentation = null;
    for (String corpus : corpora)
    {

      CorpusConfig c = null;

      if (corpusConfigurations.containsConfig(corpus))
      {
        c = corpusConfigurations.get(corpus);
      }
      else
      {
        c = corpusConfigurations.get(DEFAULT_CONFIG);
      }

      // do nothing if not even default config is set
      if (c == null)
      {
        continue;
      }

      String tmpSegment = c.getConfig(key);

      /**
       * If no segment is set in the corpus config use always the tok segment.
       */
      if (tmpSegment == null)
      {
        return corpusConfigurations.get(DEFAULT_CONFIG).getConfig(key);
      }

      if (segmentation == null)
      {
        segmentation = tmpSegment;
        continue;
      }

      if (!segmentation.equals(tmpSegment)) // return the default config
      {
        return corpusConfigurations.get(DEFAULT_CONFIG).getConfig(key);
      }
    }

    if (segmentation == null)
    {
      return corpusConfigurations.get(DEFAULT_CONFIG).getConfig(key);
    }
    else
    {
      return segmentation;
    }
  }

  /**
   * Updates the results per page combobox.
   *
   * @param resultsPerPage The value, which is added to the combobox.
   * @param keepCustomValues If this flag is true, custom values are kept.
   * Custom in a sense, that the values are not calculated with
   * {@link #generateConfig(java.util.Set)}
   *
   */
  private void updateResultsPerPage(Integer resultsPerPage,
    boolean keepCustomValues)
  {

    Set<Integer> tmpResultsPerPage = new TreeSet<>();
    if (keepCustomValues)
    {
      Collection<?> itemIds = cbResultsPerPage.getItemIds();
      Iterator<?> iterator = itemIds.iterator();

      while (iterator.hasNext())
      {
        Object next = iterator.next();
        tmpResultsPerPage.add((Integer) next);
      }
    }
    else
    {
      for (Integer i : PREDEFINED_PAGE_SIZES)
      {
        tmpResultsPerPage.add(i);
      }
    }

    tmpResultsPerPage.add(resultsPerPage);
    cbResultsPerPage.removeAllItems();

    for (Integer i : tmpResultsPerPage)
    {
      cbResultsPerPage.addItem(i);
    }

    cbResultsPerPage.setValue(resultsPerPage);
    // /update result per page
  }

  /**
   * Updates context combo boxes.
   *
   * @param c the combo box, which is updated.
   * @param maxCtx the larges context values until context steps are calculated.
   * @param ctxSteps the step range.
   * @param defaultCtx the value the combobox is set to.
   * @param keepCustomValues If this is true all custom values are kept.
   */
  private void updateContext(ComboBox c, int maxCtx, int ctxSteps,
    int defaultCtx, boolean keepCustomValues)
  {

    /**
     * The sorting via index container is much to complex for me, so I sort the
     * items first and put them afterwards into the combo boxes.
     */
    SortedSet<Integer> steps = new TreeSet<>();

    if (keepCustomValues)
    {
      Collection<?> itemIds = c.getItemIds();
      Iterator<?> iterator = itemIds.iterator();

      while (iterator.hasNext())
      {
        Object next = iterator.next();
        steps.add((Integer) next);
      }
    }
    else
    {

      for (Integer i : PREDEFINED_CONTEXTS)
      {
        if (i < maxCtx)
        {
          steps.add(i);
        }
      }

      for (int step = ctxSteps; step < maxCtx; step += ctxSteps)
      {
        steps.add(step);
      }
    }

    steps.add(maxCtx);
    steps.add(defaultCtx);

    c.removeAllItems();
    for (Integer i : steps)
    {
      c.addItem(i);
    }

    c.setValue(defaultCtx);
  }

  /**
   * Builds a Key for {@link #lastSelection} of multiple corpus selections.
   *
   * @param corpusNames A List of corpusnames, for which the key is generated.
   * @return A String which is a concatenation of all corpus names, sorted by
   * their names.
   */
  private static String buildKey(Set<String> corpusNames)
  {
    SortedSet<String> names = new TreeSet<>(corpusNames);
    StringBuilder key = new StringBuilder();

    for (String name : names)
    {
      key.append(name);
    }

    return key.toString();
  }

  public void setOptionsManuallyChanged(boolean optionsManuallyChanged)
  {
    this.optionsManuallyChanged = optionsManuallyChanged;
  }

  private class CustomResultSize implements AbstractSelect.NewItemHandler
  {

    ComboBox c;

    int resultPerPage;

    CustomResultSize(ComboBox c, int resultPerPage)
    {
      this.c = c;
      this.resultPerPage = resultPerPage;
    }

    @Override
    public void addNewItem(String resultPerPage)
    {
      if (!c.containsId(resultPerPage))
      {
        try
        {
          int i = Integer.parseInt((String) resultPerPage);

          if (i < 1)
          {
            throw new IllegalArgumentException(
              "result number has to be a positive number greater or equal than 1");
          }

          updateResultsPerPage(i, true);
        }
        catch (NumberFormatException ex)
        {
          Notification.show("invalid result per page input",
            "Please enter valid numbers [0-9]",
            Notification.Type.WARNING_MESSAGE);
        }
        catch (IllegalArgumentException ex)
        {
          Notification.show("invalid result per page input",
            ex.getMessage(), Notification.Type.WARNING_MESSAGE);
        }
      }
    }
  }

  private class CorpusConfigUpdater implements Runnable
  {
    
    private final UI ui;

    public CorpusConfigUpdater(UI ui)
    {
      this.ui = ui;
    }
    
    

    @Override
    public void run()
    {
      
      final CorpusConfigMap newCorpusConfigurations = Helper.getCorpusConfigs();

      // update GUI
      ui.access(new Runnable()
      {

        @Override
        public void run()
        {
          pbLoadConfig.setVisible(false);
          
          cbLeftContext.setVisible(true);
          cbRightContext.setVisible(true);
          cbResultsPerPage.setVisible(true);
          cbOrder.setVisible(true);
          segmentationHelp.setVisible(true);
          
          corpusConfigurations = newCorpusConfigurations;

          if (corpusConfigurations == null
            || corpusConfigurations.get(DEFAULT_CONFIG) == null
            || corpusConfigurations.get(DEFAULT_CONFIG).isEmpty())
          {
            CorpusConfig corpusConfig = new CorpusConfig();
            corpusConfig.setConfig(KEY_MAX_CONTEXT_LEFT, "5");
            corpusConfig.setConfig(KEY_MAX_CONTEXT_RIGHT, "5");
            corpusConfig.setConfig(KEY_CONTEXT_STEPS, "5");
            corpusConfig.setConfig(KEY_RESULT_PER_PAGE, "10");
            corpusConfig.setConfig(KEY_DEFAULT_CONTEXT, "5");
            corpusConfig.setConfig(KEY_DEFAULT_CONTEXT_SEGMENTATION, "tok");
            corpusConfig.setConfig(KEY_DEFAULT_BASE_TEXT_SEGMENTATION, "tok");
            corpusConfigurations = new CorpusConfigMap();
            corpusConfigurations.put(DEFAULT_CONFIG, corpusConfig);
          }

          // init the UI with the default configuration (as long as no corpus was selected)
          Integer resultsPerPage = Integer.parseInt(corpusConfigurations.get(
            DEFAULT_CONFIG).getConfig(KEY_RESULT_PER_PAGE));

          Integer leftCtx = Integer.parseInt(corpusConfigurations.
            get(DEFAULT_CONFIG).
            getConfig(KEY_MAX_CONTEXT_LEFT));

          Integer rightCtx = Integer.parseInt(
            corpusConfigurations.get(DEFAULT_CONFIG).
            getConfig(KEY_MAX_CONTEXT_RIGHT));

          Integer ctxSteps = Integer.parseInt(
            corpusConfigurations.get(DEFAULT_CONFIG).
            getConfig(KEY_CONTEXT_STEPS));
          
          cbLeftContext.setNewItemHandler(new CustomContext(cbLeftContext,
            leftCtx,
            ctxSteps));
          cbRightContext.setNewItemHandler(new CustomContext(cbRightContext,
            rightCtx,
            ctxSteps));
          cbResultsPerPage.setNewItemHandler(new CustomResultSize(
            cbResultsPerPage,
            resultsPerPage));
        }
      });
    }

  }

  private class CustomContext implements AbstractSelect.NewItemHandler
  {

    ComboBox c;

    int ctx;

    int ctxSteps;

    CustomContext(ComboBox c, int ctx, int ctxSteps)
    {
      this.c = c;
      this.ctx = ctx;
      this.ctxSteps = ctxSteps;
    }

    @Override
    public void addNewItem(String context)
    {
      if (!c.containsId(context))
      {
        try
        {
          int i = Integer.parseInt((String) context);

          if (i < 0)
          {
            throw new IllegalArgumentException(
              "context has to be a positive number or 0");
          }

          if (i > ctx)
          {
            throw new IllegalArgumentException(
              "The context is greater than, than the max value defined in the corpus property file.");
          }

          updateContext(c, ctx, ctxSteps, i, true);
        }
        catch (NumberFormatException ex)
        {
          Notification.show("invalid context input",
            "Please enter valid numbers [0-9]",
            Notification.Type.WARNING_MESSAGE);
        }
        catch (IllegalArgumentException ex)
        {
          Notification.show("invalid context input",
            ex.getMessage(),
            Notification.Type.WARNING_MESSAGE);
        }
      }
    }
  }
}
