package org.exoplatform.commons.search.driver.jcr;

import org.apache.commons.lang.StringUtils;
import org.exoplatform.commons.api.search.SearchService;
import org.exoplatform.commons.api.search.SearchServiceConnector;
import org.exoplatform.commons.api.search.data.SearchContext;
import org.exoplatform.commons.api.search.data.SearchResult;
import org.exoplatform.commons.search.service.UnifiedSearchService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.container.xml.InitParams;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JcrSearchDriver extends SearchService {
    private final static Log LOG = ExoLogger.getLogger(JcrSearchDriver.class);
  private String specialCharacters;

  public JcrSearchDriver(InitParams initParams){
    this.specialCharacters = initParams.get("exo.search.excluded-characters").toString();
  }
    @Override
    public Map<String, Collection<SearchResult>> search(SearchContext context, String query, Collection<String> sites, Collection<String> types, int offset, int limit, String sort, String order) {
        String fuzzySyntax = getFuzzySyntax();
      query = replaceSpecialCharacters(query);
        HashMap<String, ArrayList<String>> terms = parse(query); //parse query for single and quoted terms
        query = repeat("\"%s\"", terms.get("quoted"), " ") + " " + repeat("%s" + fuzzySyntax, terms.get("single"), " "); //add a fuzzySyntax after each single term (for fuzzy search)
        Map<String, Collection<SearchResult>> results = new HashMap<String, Collection<SearchResult>>();
      if(StringUtils.isBlank(query)) return results;
        if(null==types || types.isEmpty()) return results;
        List<String> enabledTypes = UnifiedSearchService.getEnabledSearchTypes();
        for(SearchServiceConnector connector:this.getConnectors()){
            if(!enabledTypes.contains(connector.getSearchType())) continue; //ignore disabled types
            if(!types.contains("all") && !types.contains(connector.getSearchType())) continue; //search requested types only
            LOG.debug("\n[UNIFIED SEARCH]: connector = " + connector.getClass().getSimpleName());
            try {
                results.put(connector.getSearchType(), connector.search(context, query, sites, offset, limit, sort, order));
            } catch (Exception e) {
                LOG.error(e.getMessage(), e);
                continue; //skip this connector and continue searching with the others
            }
        }
        return results;
    }


    private static HashMap<String, ArrayList<String>> parse(String input) {
        HashMap<String, ArrayList<String>> terms = new HashMap<String, ArrayList<String>>();

        ArrayList<String> quoted = new ArrayList<String>();
        Matcher matcher = Pattern.compile("\"([^\"]+)\"").matcher(input);
        while (matcher.find()) {
            String founds = matcher.group(1);
            quoted.add(founds);
        }
        terms.put("quoted", quoted);

        String remain = matcher.replaceAll("").replaceAll("\"", "").trim(); //remove all remaining double quotes
        ArrayList<String> single = new ArrayList<String>();
        if(!remain.isEmpty()) single.addAll(Arrays.asList(remain.split("\\s+")));
        terms.put("single", single);

        return terms;
    }

    private static String repeat(String format, Collection<String> strArr, String delimiter){
        StringBuilder sb=new StringBuilder();
        String delim = "";
        for(String str:strArr) {
            if (!isEnableFuzzySearch()){
                String disableFuzzy = str.replace("~", "");
                sb.append(delim).append(disableFuzzy);
                delim = delimiter;
            } else if (!isFuzzyManual(str) && str.indexOf("~") != -1) {
                str = str.replace(str.substring(str.indexOf("~")), "~0.5");
                sb.append(delim).append(str);
                delim = delimiter;
            }else if (!isFuzzyManual(str) && isEnableFuzzySearch()){
                sb.append(delim).append(String.format(format, str));
                delim = delimiter;
            }else {
                sb.append(delim).append(str);
                delim = delimiter;
            }
        }
        return sb.toString();
    }

    private static String getFuzzySyntax() {
        String fuzzySyntax = "";
        String fuzzySimilarity = System.getProperty("unified-search.engine.fuzzy.similarity");
        Double fuzzySimilarityDouble = 0.5;
        if (isEnableFuzzySearch()){
            if (fuzzySimilarity != null) {
                try {
                    fuzzySimilarityDouble = Double.parseDouble(fuzzySimilarity);
                } catch (NumberFormatException e) {
                    fuzzySimilarityDouble = 0.5;
                }
            }
            if (fuzzySimilarityDouble < 0 || fuzzySimilarityDouble >= 1) {
                fuzzySimilarityDouble = 0.5;
            }
            fuzzySyntax = "~" + String.valueOf(fuzzySimilarityDouble);
        }
        return fuzzySyntax;
    }

    private static boolean isFuzzyManual(String input) {
        Matcher matcher = Pattern.compile(".[~][0]([\\.][0-9])").matcher(input);
        while (matcher.find()){
            return true;
        }
        return false;
    }

    private static boolean isEnableFuzzySearch(){
        String fuzzyEnable = System.getProperty("unified-search.engine.fuzzy.enable");

        if ((fuzzyEnable!=null && Boolean.parseBoolean(fuzzyEnable)==true)
                || fuzzyEnable==null)
            return true;

        return false;
    }

  private String replaceSpecialCharacters(String query){
    return query.replaceAll("[" + specialCharacters + "]", " ");
  }
}
