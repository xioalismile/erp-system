import DOMPurify from 'dompurify'

export function sanitizeHtml(html) {
  if (!html) return ''
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS: ['b', 'i', 'u', 'strong', 'em', 'span', 'div', 'p', 'br', 'table', 'thead', 'tbody', 'tr', 'td', 'th', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'ul', 'ol', 'li', 'a', 'img'],
    ALLOWED_ATTR: ['style', 'class', 'href', 'src', 'alt', 'title', 'colspan', 'rowspan', 'width', 'height', 'align', 'valign'],
    ALLOW_DATA_ATTR: false
  })
}
