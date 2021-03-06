package uk.ac.ebi.spot.ols.controller.ui;

/**
 * @author Simon Jupp
 * @date 08/07/2015
 * Samples, Phenotypes and Ontologies Team, EMBL-EBI
 */

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;
import uk.ac.ebi.spot.ols.model.OntologyDocument;
import uk.ac.ebi.spot.ols.service.OntologyRepositoryService;

import java.text.SimpleDateFormat;
import java.util.*;

@Controller
@RequestMapping("")
public class HomeController {

    @Autowired
    OntologyRepositoryService repositoryService;

    @Autowired
    Environment environment;

    @Value("${ols.maintenance.start:#{null}}")
    String start = null;


    @Value("${ols.maintenance.end:#{null}}")
    String end = null;


    @Value("${ols.maintenance.message:#{null}}")
    String message;

    @ModelAttribute("all_ontologies")
    public List<OntologyDocument> getOntologies() {
        try {
            return repositoryService.getAllDocuments(new Sort(new Sort.Order(Sort.Direction.ASC, "ontologyId")));
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @RequestMapping({"", "/"})
    public String goHome () {
        return "redirect:index";
    }
    //
    @RequestMapping({"/index"})
    public String showHome(Model model) {

        Date lastUpdated = repositoryService.getLastUpdated();
        int numberOfOntologies = repositoryService.getNumberOfOntologies();
        int numberOfTerms = repositoryService.getNumberOfTerms();
        int numberOfProperties = repositoryService.getNumberOfProperties();
        int numberOfIndividuals = repositoryService.getNumberOfIndividuals();

        SummaryInfo summaryInfo = new SummaryInfo(lastUpdated, numberOfOntologies, numberOfTerms, numberOfProperties, numberOfIndividuals, getClass().getPackage().getImplementationVersion());

        try {
            if (isMaintenancePlanned(start)) {
                model.addAttribute("start", start);
                model.addAttribute("end", end);
                model.addAttribute("message", message);
            }
        } catch (Exception e) {
            // couldn't determine if we are in maintenance mode..
        }
        model.addAttribute("summary", summaryInfo);
        return "index";
    }

    @RequestMapping("/browse.do")
    public ModelAndView redirectOldUrls (
            @RequestParam(value = "ontName", required = true) String ontologyName,
            @RequestParam(value = "termId", required = false) String termId,
            Model model
    )  {

        ontologyName = ontologyName.toLowerCase();
        String url = "ontologies/" + ontologyName;
        if (termId != null) {
            url = "ontologies/" + ontologyName + "/terms?obo_id=" + termId;
        }
        RedirectView rv = new RedirectView(url);
        rv.setStatusCode(HttpStatus.MOVED_PERMANENTLY);
        rv.setUrl(url);
        return new ModelAndView(rv);
    }

    @RequestMapping("/search")
    public String doSearch(
            @RequestParam(value = "q", defaultValue = "*") String query,
            @RequestParam(value = "ontology", required = false) Collection<String> ontologies,
            @RequestParam(value = "type", required = false) Collection<String> types,
            @RequestParam(value= "slim", required = false) Collection<String> slims,
            @RequestParam(value = "queryFields", required = false) Collection<String> queryFields,
            @RequestParam(value = "exact", required = false) boolean exact,
            @RequestParam(value = "groupField", required = false) String groupField,
            @RequestParam(value = "obsoletes", defaultValue = "false") boolean queryObsoletes,
            @RequestParam(value = "local", defaultValue = "false") boolean isLocal,
            @RequestParam(value = "childrenOf", required = false) Collection<String> childrenOf,
            @RequestParam(value = "rows", defaultValue = "10") Integer rows,
            @RequestParam(value = "start", defaultValue = "0") Integer start,
            Model model

    ) {

        AdvancedSearchOptions searchOptions = new AdvancedSearchOptions(
                query,
                queryObsoletes,
                exact,
                isLocal,
                rows,
                start
        );

        if (ontologies != null) {
            searchOptions.setOntologies(ontologies);
        }

        if (queryFields != null) {
            searchOptions.setQueryField(queryFields);
        }

        if (types != null) {
            searchOptions.setTypes(types);
        }

        if (slims != null) {
            searchOptions.setSlims(slims);
        }

        if (groupField != null) {
            searchOptions.setGroupField(groupField);
        }


        model.addAttribute("searchOptions", searchOptions);
        return "search";
    }


    @RequestMapping({"contact"})
    public String showContact() {
        return "contact";
    }

    @RequestMapping({"maintenance"})
    public String showMaintenance(Model model) {

        try {
            model.addAttribute("scheduled", isMaintenancePlanned(start));
            model.addAttribute("maintenance", isMaintenanceMode(start, end));
        } catch (Exception e) {
            // can't determine if scheduled
        }
        model.addAttribute("start", start);
        model.addAttribute("end", end);
        model.addAttribute("message", message);
        return "maintenance";
    }

    @RequestMapping({"sparql"})
    public String showSparql() {
        return "comingsoon";
    }
    @RequestMapping({"about"})
    public String showAbout() {
        return "redirect:docs/about";
    }

    @RequestMapping({"docs"})
    public String showDocsIndex(Model model) {
        return "redirect:docs/index";
    }
    // ok, this is bad, need to find a way to deal with trailing slashes and constructing relative URLs in the thymeleaf template...
    @RequestMapping({"docs/"})
    public String showDocsIndex2(Model model) {
        return "redirect:index";
    }
    @RequestMapping({"docs/{page}"})
    public String showDocs(@PathVariable("page") String pageName, Model model) {
        model.addAttribute("page", pageName);
        return "docs-template";
    }


    private boolean isMaintenancePlanned
            (String start) throws Exception {
        if (start != null) {

            try {
                Calendar cal = Calendar.getInstance();
                Date currenTime = cal.getTime();
                SimpleDateFormat format1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                Date maintenanceStartTime = format1.parse( start );
                return currenTime.before(maintenanceStartTime);
            } catch (Exception e) {
                throw new Exception("Can't parse maintenance mode start or end time");
            }
        }
        return false;

    }

    private boolean isMaintenanceMode
            (String start, String end) throws Exception {
        if (start != null && end != null) {

            try {
                Calendar cal = Calendar.getInstance();
                Date currenTime = cal.getTime();
                SimpleDateFormat format1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                Date maintenanceStartTime = format1.parse( start );
                Date maintenanceEndTime = format1.parse( end );
                return (currenTime.after(maintenanceStartTime) && currenTime.before(maintenanceEndTime));
            } catch (Exception e) {
                throw new Exception("Can't parse maintenance mode start or end time");
            }
        }
        return false;

    }
    private class SummaryInfo {
        Date lastUpdated;
        int numberOfOntologies;
        int numberOfTerms;
        int numberOfProperties;
        int numberOfIndividuals;
        String softwareVersion;

        public SummaryInfo(Date lastUpdated, int numberOfOntologies, int numberOfTerms, int numberOfProperties, int numberOfIndividuals, String softwareVersion) {
            this.lastUpdated = lastUpdated;
            this.numberOfOntologies = numberOfOntologies;
            this.numberOfTerms = numberOfTerms;
            this.numberOfProperties = numberOfProperties;
            this.numberOfIndividuals = numberOfIndividuals;
            this.softwareVersion = softwareVersion;
        }

        public Date getLastUpdated() {
            return lastUpdated;
        }

        public void setLastUpdated(Date lastUpdated) {
            this.lastUpdated = lastUpdated;
        }

        public int getNumberOfOntologies() {
            return numberOfOntologies;
        }

        public void setNumberOfOntologies(int numberOfOntologies) {
            this.numberOfOntologies = numberOfOntologies;
        }

        public int getNumberOfTerms() {
            return numberOfTerms;
        }

        public void setNumberOfTerms(int numberOfTerms) {
            this.numberOfTerms = numberOfTerms;
        }

        public int getNumberOfProperties() {
            return numberOfProperties;
        }

        public void setNumberOfProperties(int numberOfProperties) {
            this.numberOfProperties = numberOfProperties;
        }

        public int getNumberOfIndividuals() {
            return numberOfIndividuals;
        }

        public void setNumberOfIndividuals(int numberOfIndividuals) {
            this.numberOfIndividuals = numberOfIndividuals;
        }

        public String getSoftwareVersion() {
            return softwareVersion;
        }

        public void setSoftwareVersion(String softwareVersion) {
            this.softwareVersion = softwareVersion;
        }
    }


}
