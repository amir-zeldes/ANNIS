/*
 * Copyright 2013 Corpuslinguistic working group Humboldt University Berlin.
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
package annis.gui;

import annis.examplequeries.ExampleQuery;
import annis.gui.controlpanel.ControlPanel;
import annis.gui.controlpanel.CorpusListPanel;
import annis.gui.controlpanel.QueryPanel;
import annis.gui.objects.Query;
import annis.gui.resultview.ResultViewPanel;
import annis.libgui.Helper;
import annis.libgui.PollControl;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import com.vaadin.data.Property;
import com.vaadin.event.ItemClickEvent;
import com.vaadin.ui.Button;
import com.vaadin.ui.TabSheet;
import com.vaadin.ui.Table;
import com.vaadin.data.util.BeanItemContainer;
import com.vaadin.server.FontAwesome;
import com.vaadin.server.Resource;
import com.vaadin.server.ThemeResource;
import com.vaadin.shared.ui.label.ContentMode;
import com.vaadin.ui.Component;
import com.vaadin.ui.Label;
import com.vaadin.ui.themes.BaseTheme;
import com.vaadin.ui.themes.ChameleonTheme;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps the auto generated queries.
 *
 * @author Benjamin Weißenfels <b.pixeldrama@gmail.com>
 */
public class ExampleQueriesPanel extends Table
{

  // first column String
  private final String EXAMPLE_QUERY = "example query";

  //main ui window
  private final SearchUI ui;

  // holds the current examples
  private List<ExampleQuery> examples;

  /**
   * Bean Container for example queries. Key is the corpus name.
   */
  private final BeanItemContainer<ExampleQuery> egContainer;

  // gets the
  private final static Logger log = LoggerFactory.
    getLogger(ExampleQueriesPanel.class);

  // reference to the tab which holds this component
  private TabSheet.Tab tab;

  // hold the parent tab of annis3
  private final HelpPanel parentTab;

  private static final Resource SEARCH_ICON = FontAwesome.SEARCH;

  public ExampleQueriesPanel(String caption, SearchUI ui, HelpPanel parentTab)
  {
    super(caption);
    this.ui = ui;
    this.parentTab = parentTab;

    //
    egContainer = new BeanItemContainer<>(ExampleQuery.class);
    setContainerDataSource(egContainer);
  }

  /**
   * Sets some layout properties.
   */
  private void setUpTable()
  {

    // expand the table
    setSizeFull();

    // Allow selecting items from the table.
    setSelectable(false);

    // Send changes in selection immediately to server.
    setImmediate(true);

    // set clickhandler for execute example query
    addListener(new ExampleQueryExecuter());

    // set custom style
    addStyleName("example-queries-table");

    // put stripes to the table
    addStyleName(ChameleonTheme.TABLE_STRIPED);

    setWidth(100, Unit.PERCENTAGE);

    // configure columns
    final String COLUMN_OPEN_CORPUS_BROWSER = "open corpus browser";
    addGeneratedColumn(COLUMN_OPEN_CORPUS_BROWSER, new ShowCorpusBrowser());


    addGeneratedColumn("exampleQuery", new QueryColumn());

    addGeneratedColumn("description", new ColumnGenerator()
    {
      @Override
      public Object generateCell(Table source, Object itemId, Object columnId)
      {
        ExampleQuery eQ = (ExampleQuery) itemId;
        Label l = new Label(eQ.getDescription());
        l.setContentMode(ContentMode.TEXT);
        l.addStyleName(Helper.CORPUS_FONT_FORCE);
        return l;
      }
    });

    setVisibleColumns(new Object[]
    {
      "exampleQuery",
      "description",
      COLUMN_OPEN_CORPUS_BROWSER
    });

    setColumnExpandRatio(getVisibleColumns()[0], 0.40f);
    setColumnExpandRatio(getVisibleColumns()[1], 0.40f);

    setColumnHeader(getVisibleColumns()[0], "Example Query");
    setColumnHeader(getVisibleColumns()[1], "Description");
    setColumnHeader(getVisibleColumns()[2], "open corpus browser");
  }

  @Override
  public void attach()
  {
    super.attach();

    setUpTable();

    loadExamplesFromRemote();

    addItems();
  }

  /**
   * Add items if there are any and put the example query tab in the foreground.
   */
  private void addItems()
  {
    if (examples != null && examples.size() > 0)
    {
      egContainer.addAll(examples);
      showTab();
    }
    else
    {
      hideTabSheet();
    }
  }

  /**
   * Shows the tab and put into the foreground, if no query is executed yet.
   */
  private void showTab()
  {
    if (parentTab != null)
    {
      tab = parentTab.getTab(this);
      if(tab != null)
      {
        // FIXME: this should be added by the constructor or by the panel that adds this tab
       // tab.getComponent().addStyleName("example-queries-tab");
        tab.setEnabled(true);

        if (!(parentTab.getSelectedTab() instanceof ResultViewPanel))
        {
          parentTab.setSelectedTab(tab);
        }
      }
    }
  }

  private void hideTabSheet()
  {
    if (parentTab != null)
    {
      tab = parentTab.getTab(this);

      if (tab != null)
      {
        tab.setEnabled(false);
      }
    }
  }

  private Component getOpenCorpusPanel(final String corpusName)
  {
    final Button btn = new Button(corpusName);
   
    btn.setStyleName(BaseTheme.BUTTON_LINK);
    btn.addClickListener(new Button.ClickListener()
    {
      @Override
      public void buttonClick(Button.ClickEvent event)
      {
        CorpusListPanel corpusList = ui.getControlPanel().getCorpusList();
        corpusList.initCorpusBrowser(corpusName, btn);
      }
    });

    return btn;
  }

  /**
   * Catches click events on the example query column.
   *
   * TODO do not use deprecated stuff
   */
  private class ExampleQueryExecuter implements ItemClickEvent.ItemClickListener
  {

    @Override
    public void itemClick(ItemClickEvent event)
    {
      if (event.getButton() == ItemClickEvent.BUTTON_LEFT)
      {
        String column = (String) event.getPropertyId();
        ControlPanel controlPanel = ui.getControlPanel();
        QueryPanel queryPanel;

        if (controlPanel == null)
        {
          log.error("controlPanel is not initialized");
          return;
        }

        queryPanel = controlPanel.getQueryPanel();
        if (queryPanel == null)
        {
          log.error("queryPanel is not initialized");
          return;
        }

        if (EXAMPLE_QUERY.equals(column))
        {
          Property query = event.getItem().getItemProperty(column);
          queryPanel.setQuery(query.toString());
        }
      }
    }
  }

  /**
   * Loads all available example queries.
   */
  private void loadExamplesFromRemote()
  {
    examples = loadExamplesFromRemote(null);
  }

  /**
   * Loads the available example queries for a specific corpus.
   *
   * @param corpusNames Specifies the corpora example queries are fetched for.
   * If it is null or empty all available example queries are fetched.
   */
  private static List<ExampleQuery> loadExamplesFromRemote(Set<String> corpusNames)
  {
    List<ExampleQuery> result = new LinkedList<>();
    WebResource service = Helper.getAnnisWebResource();
    try
    {
      if (corpusNames == null || corpusNames.isEmpty())
      {
        result = service.path("query").path("corpora").path(
          "example-queries").get(new GenericType<List<ExampleQuery>>()
        {
        });
      }
      else
      {
        String concatedCorpusNames = StringUtils.join(corpusNames, ",");
        result = service.path("query").path("corpora").path(
          "example-queries").queryParam("corpora", concatedCorpusNames).get(
          new GenericType<List<ExampleQuery>>()
        {
        });
      }
    }
    catch (UniformInterfaceException ex)
    {
      // ignore
    }
    catch (ClientHandlerException ex)
    {
      log.error("problems with getting example queries from remote for {}",
        corpusNames, ex);
    }
    return result;
  }

  /**
   * Sets the selected corpora and causes a reload
   *
   * @param selectedCorpora Specifies the corpora example queries are fetched
   * for. If it is null, all available example queries are fetched.
   */
  public void setSelectedCorpusInBackground(final Set<String> selectedCorpora)
  {
    PollControl.runInBackground(100, ui, new Runnable()
    {
      @Override
      public void run()
      {
        final List<ExampleQuery> result =
          loadExamplesFromRemote(selectedCorpora);

        ui.access(new Runnable()
        {
          @Override
          public void run()
          {
            examples = result;
            try
            {
              removeAllItems();
              addItems();
            }
            catch (Exception ex)
            {
              log.error("removing or adding of example queries failed for {}",
                selectedCorpora, ex);
            }
          }
        });
      }
    });

  }
  
  private class QueryColumn implements Table.ColumnGenerator
  {

    @Override
    public Object generateCell(Table source, Object itemId, Object columnId)
    {
      final ExampleQuery eQ = (ExampleQuery) itemId;
      Button btn = new Button();
      btn.setDescription("show corpus browser for " + eQ.getCorpusName());
      btn.addStyleName(ChameleonTheme.BUTTON_LINK);
      btn.setIcon(SEARCH_ICON);
      btn.setCaption(eQ.getExampleQuery());
      btn.setDescription("show results for \"" + eQ.getExampleQuery()
        + "\" in " + eQ.getCorpusName());
      btn.addStyleName(Helper.CORPUS_FONT_FORCE);
      
      btn.addClickListener(new Button.ClickListener()
      {
        @Override
        public void buttonClick(Button.ClickEvent event)
        {
          ControlPanel controlPanel = ui.getControlPanel();
          QueryPanel queryPanel;

          if (controlPanel == null)
          {
            log.error("controlPanel is not initialized");
            return;
          }

          queryPanel = controlPanel.getQueryPanel();
          if (queryPanel == null)
          {
            log.error("queryPanel is not initialized");
            return;
          }

          Set<String> corpusNameSet = new HashSet<>();
          corpusNameSet.add(eQ.getCorpusName());
          QueryController controller = ui.getQueryController();
          if (controller != null)
          {
            controller.setQuery(new Query(eQ.getExampleQuery(), corpusNameSet));
            controller.executeSearch(true);
          }
        }
      });
      return btn;
    }
  }

  private class ShowCorpusBrowser implements Table.ColumnGenerator
  {

    @Override
    public Object generateCell(Table source, Object itemId, Object columnId)
    {
      ExampleQuery eQ = (ExampleQuery) itemId;
      return getOpenCorpusPanel(eQ.getCorpusName());
    }
  }
}