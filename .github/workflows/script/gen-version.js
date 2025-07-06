module.exports = async ({process, github, context, core}) => {
    const VARIABLE_KEY = process.env.VARIABLE_KEY;
    const dateString = getCurrentDateString();

    const variables = (await github.paginate(
        github.rest.actions.listRepoVariables,
        {
            owner: context.repo.owner,
            repo: context.repo.repo,
        }
    )).data.variables;

    github.rest.actions.listRepoVariables.endpoint
    github.paginate()

    const index = variables.findIndex((v) => v.name === VARIABLE_KEY);
    if (index === -1) {
        core.notice(`Repository variable "${VARIABLE_KEY}" does not exist, create.`);
        await createVariable(VARIABLE_KEY, dateString, "1");
        return getVersionText(dateString, "1");
    }

    const oldValue = variables[index].value.split(";");
    if (oldValue.length !== 2) {
        core.warning("Variable value is invalid.");
        await updateVariable(VARIABLE_KEY, dateString, "1");
        return getVersionText(dateString, "1");
    }

    if (oldValue[0] !== dateString) {
        await updateVariable(VARIABLE_KEY, dateString, "1");
        return getVersionText(dateString, "1");
    }

    let previousSerial = parseInt(oldValue[1]);
    if (isNaN(previousSerial)) {
        core.warning("Variable value is invalid.");
        await updateVariable(VARIABLE_KEY, dateString, "1");
        return getVersionText(dateString, "1");
    }

    let serial = (++previousSerial).toString();
    await updateVariable(VARIABLE_KEY, dateString, serial);
    return getVersionText(dateString, serial);

    function getCurrentDateString() {
        const systemDate = new Date();
        const offset_min = systemDate.getTimezoneOffset() + 9 * 60;
        const jstDate = new Date(systemDate + 1_000 * 60 * offset_min);
        return jstDate
            .toLocaleDateString("ja-JP", {year: "numeric", month: "2-digit", day: "2-digit"})
            .replaceAll("/", "-");
    }

    function getVersionText(dateString, serial) {
        return `${dateString}.${serial}`
    }

    async function createVariable(key, dateString, serial) {
        await github.rest.actions.createRepoVariable({
            owner: context.repo.owner,
            repo: context.repo.repo,
            name: key,
            value: `${dateString};${serial}`,
        });
    }

    async function updateVariable(key, dateString, serial) {
        await github.rest.actions.updateRepoVariable({
            owner: context.repo.owner,
            repo: context.repo.repo,
            name: key,
            value: `${dateString};${serial}`,
        });
    }
}
