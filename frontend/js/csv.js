function textCell(value) {
  let text = String(value ?? '');
  if (/^[\s\uFEFF]*[=+\-@]/u.test(text) || /^[\t\r\n]/u.test(text)) text = `'${text}`;
  return `"${text.replaceAll('"', '""')}"`;
}

/** Descrierile sunt text, iar sumele sunt valori numerice generate de server. */
export function ledgerCsv(transactions) {
  const lines = [['Dată UTC', 'Tip', 'Descriere', 'AP', 'Sold AP', 'ID'].map(textCell).join(',')];
  for (const entry of transactions) {
    lines.push([
      textCell(entry.created_at), textCell(entry.type), textCell(entry.description),
      Number.isSafeInteger(entry.amount) ? entry.amount : '',
      Number.isSafeInteger(entry.balance_after) ? entry.balance_after : '',
      textCell(entry.id),
    ].join(','));
  }
  return new Blob(['\uFEFF', lines.join('\r\n') + '\r\n'], { type: 'text/csv;charset=utf-8' });
}
