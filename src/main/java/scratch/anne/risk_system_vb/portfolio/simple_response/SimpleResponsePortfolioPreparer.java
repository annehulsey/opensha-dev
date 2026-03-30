//package scratch.anne.risk_system_vb.portfolio.simple_response;
//
//import scratch.anne.risk_system_vb.portfolio.Portfolio;
//import scratch.anne.risk_system_vb.portfolio.assets.*;
//import scratch.anne.risk_system_vb.calc.convolution.*;
//import scratch.anne.risk_system_vb.structural_response.*;
//import scratch.anne.risk_system_vb.structural_response.vulnerabilities.VulnerabilityModel;
//import scratch.anne.risk_system_vb.io.VulnerabilityLibraryReader;
//
//import java.nio.file.Path;
//import java.util.*;
//import java.util.stream.Collectors;
//
///**
// * Utility to prepare a SimpleResponsePortfolio and filtered SimpleImResponseLibrary
// * from a base Portfolio and a full ResponseModelLibrary.
// */
//public class SimpleResponsePortfolioPreparer {
//
//    /**
//     * Prepare the filtered library and the portfolio.
//     *
//     * @param basePortfolio base portfolio of assets
//     * @param fullLibrary   full ResponseModelLibrary from CSV/JSON
//     * @return Pair of filtered library and prepared portfolio
//     */
//    public static <T extends AbstractAsset> SimpleResponsePortfolio<T> prepare(
//            Portfolio<T> basePortfolio,
//            ResponseModelLibrary<VulnerabilityModel> fullLibrary) {
//
//        // 1) Collect names of models actually included in the portfolio
//        Set<String> includedNames = basePortfolio.getAssets().stream()
//                .map(AbstractAsset::getResponseModelName)
//                .collect(Collectors.toSet());
//
//        // 2) Convert VulnerabilityModel → SimpleImResponse, only for used models
//        List<SimpleImResponse> filteredResponses = fullLibrary.getResponseModels().stream()
//                .filter(vm -> includedNames.contains(vm.getName()))
//                .map(SimpleImResponse::fromVulnerabilityModel)
//                .collect(Collectors.toList());
//
//        // 3) Build filtered SimpleImResponseLibrary
//        SimpleImResponseLibrary<SimpleImResponse> filteredLibrary =
//                new SimpleImResponseLibrary<>(
//                        filteredResponses,
//                        fullLibrary.getLibrarySource(),
//                        fullLibrary.getDescription(),
//                        fullLibrary.getAttributeUnits(),
//                        fullLibrary.getCreationInfo()
//                );
//
//        // 4) Build prepared SimpleResponsePortfolio using filtered library
//        SimpleResponsePortfolio<T> preparedPortfolio =
//                SimpleResponsePortfolio.prepareFromPortfolio(basePortfolio, filteredLibrary);
//
//        return new PreparedPortfolioResult<>(filteredLibrary, preparedPortfolio);
//    }
//
//    /**
//     * Container for result: filtered library + prepared portfolio
//     */
//    public static class PreparedPortfolioResult<T extends AbstractAsset> {
//        private final SimpleImResponseLibrary<SimpleImResponse> library;
//        private final SimpleResponsePortfolio<T> portfolio;
//
//        public PreparedPortfolioResult(SimpleImResponseLibrary<SimpleImResponse> library,
//                                       SimpleResponsePortfolio<T> portfolio) {
//            this.library = library;
//            this.portfolio = portfolio;
//        }
//
//        public SimpleImResponseLibrary<SimpleImResponse> getLibrary() { return library; }
//        public SimpleResponsePortfolio<T> getPortfolio() { return portfolio; }
//    }
//}
