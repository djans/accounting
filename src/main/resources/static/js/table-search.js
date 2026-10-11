(function () {
    document.querySelectorAll('[data-table-search]').forEach(input => {
        const rowsSelector = input.dataset.rowsSelector;
        const noMatches = document.getElementById(input.dataset.noMatchesId);

        function applyFilter() {
            const query = input.value.trim().toLocaleLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '');
            const rows = document.querySelectorAll(rowsSelector);
            let visibleRows = 0;

            rows.forEach(row => {
                const cells = Array.from(row.cells);
                const searchableText = (row.dataset.tableFilterText ?? cells.slice(0, -1)
                    .map(cell => cell.textContent)
                    .join(' '))
                    .toLocaleLowerCase()
                    .normalize('NFD')
                    .replace(/[\u0300-\u036f]/g, '');
                const visible = searchableText.includes(query);
                row.hidden = !visible;
                if (visible) {
                    visibleRows++;
                }
            });

            if (noMatches) {
                noMatches.hidden = rows.length === 0 || visibleRows > 0;
            }
            if (typeof window.updateTableTotals === 'function') {
                window.updateTableTotals();
            }
        }

        input.addEventListener('input', applyFilter);
        applyFilter();

        const observedBodies = new Set(
            Array.from(document.querySelectorAll(rowsSelector), row => row.parentElement)
                .filter(element => element !== null)
        );
        if (observedBodies.size === 0) {
            document.querySelectorAll('table tbody').forEach(body => observedBodies.add(body));
        }
        const observer = new MutationObserver(applyFilter);
        observedBodies.forEach(body => observer.observe(body, {childList: true, subtree: true}));
    });
})();
