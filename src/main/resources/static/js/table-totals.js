(function () {
    window.updateTableTotals = function () {
        document.querySelectorAll('table[data-table-totals]').forEach(table => {
            const totals = new Map();
            table.querySelectorAll('tbody tr:not([hidden]) [data-total-key]').forEach(cell => {
                const value = Number(cell.dataset.totalValue);
                if (Number.isFinite(value)) {
                    const key = cell.dataset.totalKey;
                    totals.set(key, (totals.get(key) || 0) + Math.round(value * 100));
                }
            });

            table.querySelectorAll('[data-total-cell]').forEach(cell => {
                const amount = (totals.get(cell.dataset.totalCell) || 0) / 100;
                const formatted = new Intl.NumberFormat(document.documentElement.lang || undefined, {
                    style: 'currency',
                    currency: 'CAD'
                }).format(amount);
                if (cell.textContent !== formatted) {
                    cell.textContent = formatted;
                }
            });
        });
    };

    window.updateTableTotals();
})();
