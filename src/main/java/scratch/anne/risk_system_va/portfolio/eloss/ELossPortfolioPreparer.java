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
 *  - Preserve extra group fields from the original Asset
 */
public class ELossPortfolioPreparer {

    /**
     * Prepares an ELossPortfolio from a Portfolio and vulnerability library.
     *
     * @param portfolio the original immutable portfolio
     * @param vulnLibrary the prepared ELossVulnerabilityLibrary
     * @return ELossPortfolio with assets and grouping maps, including extra fields
     */
    public static ELossPortfolio prepare(Portfolio portfolio,
                                         ELossVulnerabilityLibrary vulnLibrary) {

        List<ELossAsset> elossAssets = new ArrayList<>();

        for (Asset asset : portfolio.getAssets()) {

            // --- get vulnerability ---
            String vulnName = asset.getVulnModel();
            ELossVulnerability vuln = vulnLibrary.getByName(vulnName);

            // --- create ELossAsset (constructor copies extra fields if present) ---
            ELossAsset elossAsset = new ELossAsset(asset, vuln);

            elossAssets.add(elossAsset);
        }

        // --- pass to ELossPortfolio constructor ---
        // This will automatically create all site, site+IM, and extra field groupings
        return new ELossPortfolio(elossAssets);
    }
}