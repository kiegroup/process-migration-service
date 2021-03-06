import React from "react";
import PropTypes from "prop-types";

import { Button, Icon, Wizard } from "patternfly-react";

import MigrationClient from "../../../clients/migrationClient";
import { ExecuteMigrationItems } from "../../common/WizardItems";
import PageMigrationRunningInstances from "./PageMigrationRunningInstances";
import PageReview from "../PageReview";
import { renderWizardSteps } from "../PfWizardRenderers";
import WizardBase from "../WizardBase";

import PageMigrationScheduler from "./PageMigrationScheduler";
import { ALERT_TYPE_ERROR } from "patternfly-react/dist/js/components/Alert/AlertConstants";
import Notification from "../../Notification";
import kieServerClient from "../../../clients/kieServerClient";

export default class WizardExecuteMigration extends WizardBase {
  constructor(props) {
    super(props, ExecuteMigrationItems);
    this.state = {
      activeStepIndex: 0,
      activeSubStepIndex: 0,
      scheduledStartTime: "",
      callbackUrl: "",
      definition: {
        planId: props.planId,
        kieServerId: props.kieServerId,
        execution: {
          type: "ASYNC"
        },
        processInstanceIds: []
      },
      migration: {},
      stepValidation: {}
    };
  }

  onSubmitMigrationPlan = () => {
    MigrationClient.create(this.state.definition)
      .then(migration => {
        this.setState({ migration: migration });
        this.onNextButtonClick();
      })
      .catch(() => {
        this.setState({
          errorMsg: `Unable to execute migration on KIE Server ${this.props.kieServerId}`
        });
      });
  };

  getRunningInstances = (page, perPage, sortingColumn) => {
    const sorting = this.translateRunningInstancesColumn(sortingColumn);
    return kieServerClient.getInstances(
      this.props.kieServerId,
      this.props.containerId,
      page,
      perPage,
      sorting.column,
      sorting.order
    );
  };

  translateRunningInstancesColumn = sortingColumns => {
    const columnKeys = Object.keys(sortingColumns);
    if (columnKeys.length > 0) {
      return {
        column: columnKeys[0],
        order: sortingColumns[columnKeys[0]].direction
      };
    }
  };

  setRunningInstancesIds = ids => {
    const { definition } = this.state;
    definition.processInstanceIds = ids;
    if (ids.length > 0) {
      this.setState({ migrateAll: false });
    }
  };

  onExecutionFieldChange = (field, value) => {
    const { definition } = this.state;
    if (value === null) {
      delete definition.execution[field];
    } else {
      definition.execution[field] = value;
    }
    this.setState({ definition });
  };

  setStepIsValid = (step, isValid) => {
    const { stepValidation } = this.state;
    stepValidation[step] = isValid;
    this.setState({ stepValidation });
  };

  isStepValid = step => {
    return this.state.stepValidation[step];
  };

  onMigrateAllButtonClick = () => {
    this.setState({ migrateAll: true });
    this.onNextButtonClick();
  };

  render() {
    const { activeStepIndex, activeSubStepIndex } = this.state;

    const renderExecuteMigrationWizardContents = wizardSteps => {
      return wizardSteps.map((step, stepIndex) =>
        step.subSteps.map((sub, subStepIndex) => {
          if (stepIndex === 0) {
            // render steps 0
            return (
              <Wizard.Contents
                key={subStepIndex}
                stepIndex={stepIndex}
                subStepIndex={subStepIndex}
                activeStepIndex={activeStepIndex}
                activeSubStepIndex={activeSubStepIndex}
              >
                {this.state.errorMsg && (
                  <Notification
                    type={ALERT_TYPE_ERROR}
                    message={this.state.errorMsg}
                    onDismiss={() => this.setState({ errorMsg: "" })}
                  />
                )}
                <PageMigrationRunningInstances
                  getRunningInstances={this.getRunningInstances}
                  setRunningInstancesIds={this.setRunningInstancesIds}
                  onIsValid={isValid => this.setStepIsValid(0, isValid)}
                  migrateAll={this.state.migrateAll}
                />
              </Wizard.Contents>
            );
          } else if (stepIndex === 1) {
            // render steps 1
            return (
              <Wizard.Contents
                key={subStepIndex}
                stepIndex={stepIndex}
                subStepIndex={subStepIndex}
                activeStepIndex={activeStepIndex}
                activeSubStepIndex={activeSubStepIndex}
              >
                <PageMigrationScheduler
                  callbackUrl={this.state.definition.execution.callbackUrl}
                  scheduledStartTime={
                    this.state.definition.execution.scheduledStartTime
                  }
                  onFieldChange={this.onExecutionFieldChange}
                  onIsValid={isValid => this.setStepIsValid(1, isValid)}
                />
              </Wizard.Contents>
            );
          } else if (stepIndex === 2) {
            // render review
            return (
              <Wizard.Contents
                key={subStepIndex}
                stepIndex={stepIndex}
                subStepIndex={subStepIndex}
                activeStepIndex={activeStepIndex}
                activeSubStepIndex={activeSubStepIndex}
              >
                {this.state.errorMsg && (
                  <Notification
                    type={ALERT_TYPE_ERROR}
                    message={this.state.errorMsg}
                    onDismiss={() => this.setState({ errorMsg: "" })}
                  />
                )}
                <PageReview
                  object={this.state.definition}
                  exportedFileName="migration_definition"
                />
              </Wizard.Contents>
            );
          } else if (stepIndex === 3) {
            // render result page
            return (
              <Wizard.Contents
                key={subStepIndex}
                stepIndex={stepIndex}
                subStepIndex={subStepIndex}
                activeStepIndex={activeStepIndex}
                activeSubStepIndex={activeSubStepIndex}
              >
                <PageReview
                  object={this.state.migration}
                  exportedFileName="migration_execution"
                />
              </Wizard.Contents>
            );
          }
          return null;
        })
      );
    };

    return (
      <div>
        <form className="form-horizontal" name="form_migration">
          <Wizard show={this.props.isOpen}>
            <Wizard.Header
              onClose={this.props.onClose}
              title="Execute Migration Plan Wizard"
            />
            <Wizard.Body>
              <Wizard.Steps
                steps={renderWizardSteps(
                  this.steps,
                  activeStepIndex,
                  activeSubStepIndex,
                  this.onStepClick
                )}
              />
              <Wizard.Row>
                <Wizard.Main>
                  {renderExecuteMigrationWizardContents(
                    this.steps,
                    this.state,
                    this.setInfo
                  )}
                </Wizard.Main>
              </Wizard.Row>
            </Wizard.Body>
            <Wizard.Footer>
              {activeStepIndex !== 3 && (
                <React.Fragment>
                  <Button
                    bsStyle="default"
                    className="btn-cancel"
                    onClick={this.props.onClose}
                  >
                    Cancel
                  </Button>
                  <Button
                    bsStyle="default"
                    disabled={activeStepIndex === 0 && activeSubStepIndex === 0}
                    onClick={this.onBackButtonClick}
                  >
                    <Icon type="fa" name="angle-left" />
                    Back
                  </Button>
                </React.Fragment>
              )}
              {activeStepIndex === 0 && (
                <Button
                  bsStyle="primary"
                  onClick={this.onMigrateAllButtonClick}
                >
                  Migrate all
                  <Icon type="fa" name="angle-right" />
                </Button>
              )}
              {(activeStepIndex === 0 || activeStepIndex === 1) && (
                <Button
                  bsStyle="primary"
                  disabled={!this.isStepValid(activeStepIndex)}
                  onClick={this.onNextButtonClick}
                >
                  Next
                  <Icon type="fa" name="angle-right" />
                </Button>
              )}
              {activeStepIndex === 2 && (
                <Button bsStyle="primary" onClick={this.onSubmitMigrationPlan}>
                  Finish
                </Button>
              )}
              {activeStepIndex === 3 && (
                <Button bsStyle="primary" onClick={this.props.onClose}>
                  Close
                </Button>
              )}
            </Wizard.Footer>
          </Wizard>
        </form>
      </div>
    );
  }
}

WizardExecuteMigration.propTypes = {
  planId: PropTypes.number.isRequired,
  kieServerId: PropTypes.string.isRequired,
  containerId: PropTypes.string.isRequired,
  isOpen: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired
};
