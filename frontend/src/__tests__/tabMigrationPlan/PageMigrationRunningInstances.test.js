import renderer from "react-test-renderer";
import React from "react";
import PageMigrationRunningInstances
  from "../../component/tabMigrationPlan/wizardExecuteMigration/PageMigrationRunningInstances";
import running_instances from "../../../mock_data/running_instances.json";

test("PageMigrationRunningInstances renders correctly using snapshot", () => {
  const myMock = jest.fn();
  const getRunningInstancesFn = jest.fn(() => {
    return new Promise(function (resolve) {
      resolve({
        data: running_instances,
        headers: [{ "x-total-count": 31 }]
      })
    })
  });
  const tree = renderer
    .create(
      <PageMigrationRunningInstances
        getRunningInstances={getRunningInstancesFn}
        setRunningInstancesIds={myMock}
        onIsValid={myMock}
        migrateAll={false}
      />
    )
    .toJSON();
  expect(tree).toMatchSnapshot();
  expect(getRunningInstancesFn).toBeCalledTimes(1);
});
