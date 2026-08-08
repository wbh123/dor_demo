export type SiteTheme = 'blue' | 'green'

export function normalizeSiteTheme(value: unknown): SiteTheme {
  return value === 'green' ? 'green' : 'blue'
}

export function applySiteTheme(value: unknown): SiteTheme {
  const theme = normalizeSiteTheme(value)
  document.documentElement.dataset.wustTheme = theme
  return theme
}

export function clearSiteTheme() {
  delete document.documentElement.dataset.wustTheme
}
