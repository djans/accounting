(function () {
    function parseValue(td, numeric) {
        const text = (td.textContent || '').trim();
        if (numeric) {
            let s = text.replace(/[\s,$€£¥]/g, '');
            const neg = /^\(.+\)$/.test(s);
            s = s.replace(/[()]/g, '');
            const n = parseFloat(s);
            if (!isNaN(n)) return neg ? -n : n;
        }
        return text.toLowerCase();
    }

    function cmp(a, b) {
        if (typeof a === 'number' && typeof b === 'number') return a - b;
        return String(a).localeCompare(String(b), undefined, { numeric: true });
    }

    function sortTable(table, idx, th, numeric) {
        const tbody = table.tBodies[0];
        if (!tbody) return;
        const current = th.getAttribute('data-sort-dir');
        const dir = current === 'asc' ? 'desc' : 'asc';

        table.querySelectorAll('th').forEach(other => {
            other.removeAttribute('data-sort-dir');
            const arr = other.querySelector('.sort-arrow');
            if (arr) arr.textContent = '';
        });
        th.setAttribute('data-sort-dir', dir);
        const arrow = th.querySelector('.sort-arrow');
        if (arrow) arrow.textContent = dir === 'asc' ? ' ▲' : ' ▼';

        const rows = Array.from(tbody.rows);
        rows.sort((r1, r2) => {
            const c1 = r1.cells[idx];
            const c2 = r2.cells[idx];
            if (!c1 || !c2) return 0;
            const result = cmp(parseValue(c1, numeric), parseValue(c2, numeric));
            return dir === 'asc' ? result : -result;
        });
        rows.forEach(r => tbody.appendChild(r));
    }

    function wireTable(table) {
        const head = table.tHead;
        if (!head || !head.rows.length) return;
        const ths = head.rows[0].cells;
        const firstRow = table.tBodies[0] && table.tBodies[0].rows[0];

        Array.from(ths).forEach((th, i) => {
            const label = (th.textContent || '').trim();
            if (!label) return; // skip action / blank columns

            const numeric = th.classList.contains('num') ||
                (firstRow && firstRow.cells[i] && firstRow.cells[i].classList.contains('num'));

            th.style.cursor = 'pointer';
            th.style.userSelect = 'none';
            th.classList.add('sortable-header');

            const arrow = document.createElement('span');
            arrow.className = 'sort-arrow';
            arrow.style.opacity = '0.55';
            arrow.style.fontSize = '0.75em';
            arrow.style.marginLeft = '0.25rem';
            th.appendChild(arrow);

            th.addEventListener('click', () => sortTable(table, i, th, numeric));
        });
    }

    document.addEventListener('DOMContentLoaded', () => {
        document.querySelectorAll('table.table-portal').forEach(wireTable);
    });
})();
