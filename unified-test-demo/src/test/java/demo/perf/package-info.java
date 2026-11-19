/**
 * Performance Pairs: for each scenario, one Unified UI Test and a Direct Test on each Testing Tool, held to
 * the same user actions and checks so their time and round trips can be compared (spec chapter 13, #294).
 *
 * <p>Tagged {@code performance}, so the normal run leaves them out; run them with
 * {@code mvn verify -Pperformance -Dvaadin.testing.tool=browserless|playwright|selenium}. Each Direct Test runs
 * only on its own Testing Tool; the Unified UI Test runs on all three.
 *
 * <p>The scenarios, their user actions and their checks, the same in all four tests of a pair:
 *
 * <table>
 * <caption>Scenarios</caption>
 * <tr><th>Scenario</th><th>View</th><th>User actions</th><th>Check</th></tr>
 * <tr><td>Small form</td><td>{@code profile}</td>
 *     <td>fill Name "Alice"; fill Email "alice@example.com"; click Save</td>
 *     <td>notification "Saved Alice"</td></tr>
 * <tr><td>Long form</td><td>{@code perf/long-form}</td>
 *     <td>fill the 24 text fields, 2 integer fields and 2 date pickers ({@link demo.perf.PerfData}); check
 *         the 2 checkboxes; click Save</td>
 *     <td>result "Saved 30 of 30 fields"</td></tr>
 * <tr><td>Large grid</td><td>{@code perf/large-grid}</td>
 *     <td>find the row whose Name is "Person 250" and click it</td>
 *     <td>Name field "Person 250", Email field "person250@example.com"</td></tr>
 * <tr><td>Combo box</td><td>{@code perf/combo-box}</td>
 *     <td>pick "Item 0742" in Product</td>
 *     <td>"Picked: Item 0742"</td></tr>
 * </table>
 *
 * <p>Fairness rule: the same actions and checks in the same order; each Direct Test written as a skilled
 * user of its tool would, with the waits that tool needs; no Escape Hatch and no Migration Markers in the
 * Unified UI Tests; default settings.
 */
package demo.perf;
