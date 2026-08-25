/**
 * Web plumbing shared by all controllers: the correlation-id filter, the pagination envelope and
 * {@code ETag} support.
 *
 * <p><b>Rule:</b> no endpoint of its own beyond infrastructure concerns — business endpoints live in
 * a context's {@code api} package.
 */
package app.giaky.common.web;
