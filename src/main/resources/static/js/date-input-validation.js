(function () {
    const maximumDate = '9999-12-31';
    const formatMessage = window.dateInputFormatMessage
        || 'Enter a valid date with a four-digit year.';

    function isValidDate(value) {
        const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
        if (!match) return false;

        const year = Number(match[1]);
        const month = Number(match[2]);
        const day = Number(match[3]);
        if (year < 1 || month < 1 || month > 12) return false;

        const leapYear = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
        const daysInMonth = [31, leapYear ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
        return day >= 1 && day <= daysInMonth[month - 1];
    }

    function validateDateInput(input) {
        if (!input.max || input.max > maximumDate) {
            input.max = maximumDate;
        }
        input.setCustomValidity(input.value && !isValidDate(input.value) ? formatMessage : '');
    }

    document.querySelectorAll('input[type="date"]').forEach(function (input) {
        validateDateInput(input);
        input.addEventListener('input', function () {
            validateDateInput(input);
        });
        input.addEventListener('change', function () {
            validateDateInput(input);
        });
    });
})();
