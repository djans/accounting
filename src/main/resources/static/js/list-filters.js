(function () {
    const dateToIso = date => {
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        return `${year}-${month}-${day}`;
    };

    function rangeFor(filter) {
        const today = new Date();
        const year = today.getFullYear();
        const preset = filter.querySelector('[data-date-preset]').value;

        switch (preset) {
            case 'this-month':
                return {
                    start: dateToIso(new Date(year, today.getMonth(), 1)),
                    end: dateToIso(new Date(year, today.getMonth() + 1, 0))
                };
            case 'current-period':
                return {
                    start: filter.dataset.currentPeriodStart,
                    end: filter.dataset.currentPeriodEnd
                };
            case 'previous-period':
                return {
                    start: filter.dataset.previousPeriodStart,
                    end: filter.dataset.previousPeriodEnd
                };
            case 'this-year':
                return {start: `${year}-01-01`, end: `${year}-12-31`};
            case 'last-year':
                return {start: `${year - 1}-01-01`, end: `${year - 1}-12-31`};
            case 'custom':
                return {
                    start: filter.querySelector('[data-date-from]').value,
                    end: filter.querySelector('[data-date-to]').value
                };
            default:
                return {start: '', end: ''};
        }
    }

    document.querySelectorAll('[data-list-filter]').forEach(filter => {
        const preset = filter.querySelector('[data-date-preset]');
        const customRange = filter.querySelector('[data-custom-date-range]');
        const customerFilterId = filter.dataset.customerFilterId;
        const customerFilter = customerFilterId
            ? document.getElementById(customerFilterId)
            : null;
        const rows = document.querySelectorAll(filter.dataset.rowsSelector);
        const noMatches = document.querySelector(filter.dataset.noMatchesSelector);
        const rangeError = filter.querySelector('[data-filter-range-error]');

        function applyFilters() {
            const range = rangeFor(filter);
            const invalidRange = Boolean(range.start && range.end && range.start > range.end);
            const selectedCustomer = customerFilter ? customerFilter.value : '';
            let visibleRows = 0;

            if (rangeError) {
                rangeError.hidden = !invalidRange;
            }

            rows.forEach(row => {
                const rowDate = row.dataset.listDate || '';
                const matchesDate = !invalidRange
                    && (!range.start || (rowDate && rowDate >= range.start))
                    && (!range.end || (rowDate && rowDate <= range.end));
                const matchesCustomer = !selectedCustomer
                    || row.dataset.listCustomerId === selectedCustomer;
                const visible = matchesDate && matchesCustomer;
                row.hidden = !visible;
                if (visible) {
                    visibleRows++;
                }
            });

            if (noMatches) {
                noMatches.hidden = rows.length === 0 || visibleRows > 0 || invalidRange;
            }
        }

        function updateControls() {
            customRange.hidden = preset.value !== 'custom';
            applyFilters();
        }

        preset.addEventListener('change', updateControls);
        filter.querySelectorAll('[data-date-from], [data-date-to]')
            .forEach(input => input.addEventListener('change', applyFilters));
        if (customerFilter) {
            customerFilter.addEventListener('change', applyFilters);
        }
        updateControls();
    });
})();
