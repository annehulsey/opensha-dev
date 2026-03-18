package scratch.anne.risk_system_va.portfolio.eloss;

import scratch.anne.risk_system_va.portfolio.Portfolio;
import scratch.anne.risk_system_va.portfolio.Asset;
import scratch.anne.risk_system_va.calc.eloss.ELossVulnerability;
import scratch.anne.risk_system_va.calc.eloss.ELossVulnerabilityLibrary;

import java.util.*;

/**
 * Prepares an ELossPortfolio from a Portfolio and an ELossVulnerabilityLibrary.
 *
 * Responsibilities:
 *  - Convert Portfolio assets into ELossAsset objects
 *  - Build SiteKey and ImKey for grouping
 *  - Build grouping maps for site and site+IM
 */
public class ELossPortfolioPreparer {

    /**
     * Prepares an ELossPortfolio from a Portfolio and vulnerability library.
     *
     * @param portfolio the original immutable portfolio
     * @param vulnLibrary the prepared ELossVulnerabilityLibrary
     * @return ELossPortfolio with assets and grouping maps
     */
    public static ELossPortfolio prepare(Portfolio portfolio,
                                         ELossVulnerabilityLibrary vulnLibrary) {

        List<ELossAsset> elossAssets = new ArrayList<>();

        Map<ELossAsset.SiteKey, List<ELossAsset>> assetsBySite = new HashMap<>();
        Map<ELossAsset.SiteKey, Map<ELossAsset.ImKey, List<ELossAsset>>> assetsBySiteAndIm = new HashMap<>();

        for (Asset asset : portfolio.getAssets()) {

            // --- extract site information ---
            double lat = asset.getLat();
            double lon = asset.getLon();
            double vs30 = asset.getVs30();

            ELossAsset.SiteKey siteKey = new ELossAsset.SiteKey(lat, lon, vs30);

            // --- get vulnerability ---
            String vulnName = asset.getVulnModel();
            System.out.println(vulnName);
            ELossVulnerability vuln = vulnLibrary.getByName(vulnName);

            // --- build IM key for grouping ---
            ELossAsset.ImKey imKey = new ELossAsset.ImKey(vuln.getImtString(), vuln.getLogImValues());

            // --- create ELossAsset ---
            ELossAsset elossAsset = new ELossAsset(asset, vuln);

            elossAssets.add(elossAsset);

            // --- add to site grouping ---
            assetsBySite
                    .computeIfAbsent(siteKey, k -> new ArrayList<>())
                    .add(elossAsset);

            // --- add to site + IM grouping ---
            assetsBySiteAndIm
                    .computeIfAbsent(siteKey, k -> new HashMap<>())
                    .computeIfAbsent(imKey, k -> new ArrayList<>())
                    .add(elossAsset);
        }

//        return new ELossPortfolio(elossAssets, assetsBySite, assetsBySiteAndIm);
        return new ELossPortfolio(elossAssets);
    }
}