// Generated by N4JS transpiler; for copyright see original N4JS source file.

(function(System) {
	'use strict';
	System.register([
		'n4js.lang/src-gen/n4js/lang/N4Injector',
		'org.eclipse.n4js.mangelhaft/src-gen/org/eclipse/n4js/mangelhaft/InstrumentedTest',
		'org.eclipse.n4js.mangelhaft/src-gen/org/eclipse/n4js/mangelhaft/TestExecutor',
		'org.eclipse.n4js.mangelhaft.assert/src-gen/org/eclipse/n4js/mangelhaft/precondition/PreconditionNotMet',
		'org.eclipse.n4js.mangelhaft/src-gen/org/eclipse/n4js/mangelhaft/types/ResultGroup',
		'org.eclipse.n4js.mangelhaft/src-gen/org/eclipse/n4js/mangelhaft/types/ResultGroups',
		'org.eclipse.n4js.mangelhaft/src-gen/org/eclipse/n4js/mangelhaft/types/TestFunctionType',
		'org.eclipse.n4js.mangelhaft/src-gen/org/eclipse/n4js/mangelhaft/types/TestMethodDescriptor',
		'org.eclipse.n4js.mangelhaft/src-gen/org/eclipse/n4js/mangelhaft/types/TestSpy'
	], function($n4Export) {
		var N4Injector, InstrumentedTest, TestExecutor, PreconditionNotMet, ResultGroup, ResultGroups, TestFunctionType, TestMethodDescriptor, TestSpy, MAX_GROUPS_PER_TEST_BATCH, notNull, TestController;
		TestController = function TestController() {
			this.spy = undefined;
			this.executor = undefined;
			this.injector = undefined;
			this.reportersVal = [];
		};
		$n4Export('TestController', TestController);
		return {
			setters: [
				function($exports) {
					// n4js.lang/src-gen/n4js/lang/N4Injector
					N4Injector = $exports.N4Injector;
				},
				function($exports) {
					// org.eclipse.n4js.mangelhaft/src-gen/org/eclipse/n4js/mangelhaft/InstrumentedTest
					InstrumentedTest = $exports.InstrumentedTest;
				},
				function($exports) {
					// org.eclipse.n4js.mangelhaft/src-gen/org/eclipse/n4js/mangelhaft/TestExecutor
					TestExecutor = $exports.TestExecutor;
				},
				function($exports) {
					// org.eclipse.n4js.mangelhaft.assert/src-gen/org/eclipse/n4js/mangelhaft/precondition/PreconditionNotMet
					PreconditionNotMet = $exports.PreconditionNotMet;
				},
				function($exports) {
					// org.eclipse.n4js.mangelhaft/src-gen/org/eclipse/n4js/mangelhaft/types/ResultGroup
					ResultGroup = $exports.ResultGroup;
				},
				function($exports) {
					// org.eclipse.n4js.mangelhaft/src-gen/org/eclipse/n4js/mangelhaft/types/ResultGroups
					ResultGroups = $exports.ResultGroups;
				},
				function($exports) {
					// org.eclipse.n4js.mangelhaft/src-gen/org/eclipse/n4js/mangelhaft/types/TestFunctionType
					TestFunctionType = $exports.TestFunctionType;
				},
				function($exports) {
					// org.eclipse.n4js.mangelhaft/src-gen/org/eclipse/n4js/mangelhaft/types/TestMethodDescriptor
					TestMethodDescriptor = $exports.TestMethodDescriptor;
				},
				function($exports) {
					// org.eclipse.n4js.mangelhaft/src-gen/org/eclipse/n4js/mangelhaft/types/TestSpy
					TestSpy = $exports.TestSpy;
				}
			],
			execute: function() {
				MAX_GROUPS_PER_TEST_BATCH = 10;
				notNull = (thing)=>thing !== null;
				$makeClass(TestController, N4Object, [], {
					runGroups: {
						value: async function runGroups___n4(testCatalog, numTests, scope) {
							if (!testCatalog) {
								throw new Error("TestController::runGroups called with a null testCatalog");
							}
							const testInfos = testCatalog.testDescriptors.sort((x, y)=>{
								let xVal = x.fqn ? x.fqn : x.module;
								let yVal = y.fqn ? y.fqn : y.module;
								return xVal.localeCompare(yVal);
							});
							if (numTests === undefined) {
								numTests = testInfos.reduce((acc, info)=>acc + info.testMethods.length, 0);
							}
							try {
								await this.spy.testingStarted.dispatch([
									testInfos.length,
									testCatalog.sessionId,
									numTests
								]);
							} catch(ex) {
								console.log("testingStarted.dispatch is bad", ex);
							}
							const res = await this.runInBatches(testInfos, scope);
							await this.spy.testingFinished.dispatch([
								res
							]);
							return res;
						}
					},
					runInBatches: {
						value: async function runInBatches___n4(testInfos, scope) {
							let res;
							const batchedTestInfos = [];
							for(let batchedCount = 0;batchedCount < testInfos.length;batchedCount += MAX_GROUPS_PER_TEST_BATCH) {
								const batch = testInfos.slice(batchedCount, batchedCount + MAX_GROUPS_PER_TEST_BATCH);
								batchedTestInfos.push(batch);
							}
							const mapper = async(testInfo)=>await this.instrument(testInfo);
							const flatten = (arr)=>Array.prototype.concat.apply([], arr);
							const instrumentBatch = async function(testInfosBatch) {
								const instrumentedBatches = testInfosBatch.map(mapper).filter(notNull);
								const instrumentedTestsBatch1d = await Promise.all(instrumentedBatches);
								const instrumentedTestsBatch2d = await Promise.resolve(instrumentedTestsBatch1d);
								const instrumentedTestsBatch = flatten(instrumentedTestsBatch2d);
								return instrumentedTestsBatch;
							};
							const resultGroups = [];
							for(let numOfBatches = 0;numOfBatches < batchedTestInfos.length;++numOfBatches) {
								try {
									const testInfosBatch = batchedTestInfos[numOfBatches];
									const instrumentedTestsBatch = await instrumentBatch(testInfosBatch);
									res = await this.executor.runTestsAsync(instrumentedTestsBatch, scope);
									resultGroups.push(res);
								} catch(er) {
									console.error(er);
									throw er;
								}
							}
							res = ResultGroups.concatArray(resultGroups);
							return res;
						}
					},
					instrument: {
						value: async function instrument___n4(info) {
							let testClasses;
							const parts = info.fqn.split("/");
							const ctorName = parts.pop();
							const moduleName = parts.join("/");
							try {
								const groupModule = System.throwPendingError(await System.import(info.origin + "/" + moduleName));
								testClasses = [
									groupModule[ctorName]
								];
							} catch(ex) {
								await this.errorGroup(info, info.origin + "/" + moduleName, null, ex);
								return null;
							}
							if (!testClasses) {
								await this.errorGroup(info, info.origin + "/" + parts.join("/"));
								return null;
							}
							let instrumentedTests = [];
							for(const testClass of testClasses) {
								if (!testClass) {
									await this.errorGroup(info, info.origin + "/" + moduleName, null, new Error("Empty object loaded (is the test class exported?)"));
									continue;
								}
								try {
									const testInjector = this.getTestInjector(testClass);
									let classITO = InstrumentedTest.getInstrumentedTest(testClass, info, testInjector, this);
									instrumentedTests.push(classITO);
								} catch(ex2) {
									const stubTestInstrumentationError = new InstrumentedTest(testClass, info).setTestObject(new N4Object()).setError(ex2);
									instrumentedTests.push(stubTestInstrumentationError);
								}
							}
							const result = (await Promise.all(instrumentedTests)).filter(notNull);
							return result;
						}
					},
					errorGroup: {
						value: async function errorGroup___n4(info, loadPath, testObject, originalError) {
							if (!testObject) {
								testObject = this.getInstrumentedTestStub(info);
							}
							await this.spy.groupStarted.dispatch([
								testObject
							]);
							const testResults = [];
							const that = this;
							for(const test of testObject.tests) {
								await that.spy.testStarted.dispatch([
									testObject,
									test
								]);
								const error = originalError ? originalError : new Error("could not load test " + loadPath);
								const testResult = TestExecutor.generateFailureTestResult(error, "could not load test " + loadPath);
								testResults.push(testResult);
								await that.spy.testFinished.dispatch([
									testObject,
									test,
									testResult
								]);
							}
							await this.spy.groupFinished.dispatch([
								testObject,
								new ResultGroup(testResults, `${info.fqn} ${testObject.parameterizedName}`)
							]);
							return true;
						}
					},
					getInstrumentedTestStub: {
						value: function getInstrumentedTestStub___n4(info) {
							const testObject = new InstrumentedTest();
							info.module = info.module || "";
							info.fqn = info.fqn || info.module.replace(/\//g, ".") + ".*";
							testObject.load(N4Object, info).setTestObject(new N4Object());
							if (info.testMethods) {
								testObject.tests = info.testMethods.map((methName)=>new TestMethodDescriptor({
									name: methName,
									type: TestFunctionType.TEST,
									value: function() {}
								}));
							} else {
								testObject.tests = [
									new TestMethodDescriptor({
										name: "",
										type: TestFunctionType.TEST,
										value: function() {}
									})
								];
							}
							return testObject;
						}
					},
					getTestInjector: {
						value: function getTestInjector___n4(testClass) {
							let testInjector;
							let diClass = testClass;
							let testType = testClass.n4type;
							while(testType) {
								if (testType.hasAnnotation("GenerateInjector")) {
									if (testType.hasAnnotation("WithParentInjector")) {
										if (!this.injector.canBeParentOf(diClass)) {
											throw new PreconditionNotMet("Test called with incompatible parent injector");
										}
										testInjector = N4Injector.of(diClass, this.injector);
									} else {
										testInjector = N4Injector.of(diClass);
									}
									break;
								}
								testType = testType.n4superType;
								diClass = Object.getPrototypeOf(diClass);
							}
							if (!testType) {
								testInjector = this.injector;
							}
							return testInjector;
						}
					},
					reporters: {
						get: function getReporters___n4() {
							return this.reportersVal;
						},
						set: function setReporters___n4(reporters) {
							reporters.forEach(function(reporter) {
								let dummy = reporter.register();
								dummy;
							});
							this.reportersVal = reporters;
						}
					},
					spy: {
						value: undefined,
						writable: true
					},
					executor: {
						value: undefined,
						writable: true
					},
					injector: {
						value: undefined,
						writable: true
					},
					reportersVal: {
						value: undefined,
						writable: true
					}
				}, {}, function(instanceProto, staticProto) {
					var metaClass = new N4Class({
						name: 'TestController',
						origin: 'org.eclipse.n4js.mangelhaft',
						fqn: 'org.eclipse.n4js.mangelhaft.TestController.TestController',
						n4superType: N4Object.n4type,
						allImplementedInterfaces: [],
						ownedMembers: [
							new N4DataField({
								name: 'spy',
								isStatic: false,
								annotations: [
									new N4Annotation({
										name: 'Inject',
										details: []
									})
								]
							}),
							new N4DataField({
								name: 'executor',
								isStatic: false,
								annotations: [
									new N4Annotation({
										name: 'Inject',
										details: []
									})
								]
							}),
							new N4DataField({
								name: 'injector',
								isStatic: false,
								annotations: [
									new N4Annotation({
										name: 'Inject',
										details: []
									})
								]
							}),
							new N4DataField({
								name: 'reportersVal',
								isStatic: false,
								annotations: []
							}),
							new N4Accessor({
								name: 'reporters',
								getter: true,
								isStatic: false,
								annotations: []
							}),
							new N4Accessor({
								name: 'reporters',
								getter: false,
								isStatic: false,
								annotations: []
							}),
							new N4Method({
								name: 'runGroups',
								isStatic: false,
								jsFunction: instanceProto['runGroups'],
								annotations: []
							}),
							new N4Method({
								name: 'runInBatches',
								isStatic: false,
								jsFunction: instanceProto['runInBatches'],
								annotations: []
							}),
							new N4Method({
								name: 'instrument',
								isStatic: false,
								jsFunction: instanceProto['instrument'],
								annotations: []
							}),
							new N4Method({
								name: 'errorGroup',
								isStatic: false,
								jsFunction: instanceProto['errorGroup'],
								annotations: []
							}),
							new N4Method({
								name: 'getInstrumentedTestStub',
								isStatic: false,
								jsFunction: instanceProto['getInstrumentedTestStub'],
								annotations: []
							}),
							new N4Method({
								name: 'getTestInjector',
								isStatic: false,
								jsFunction: instanceProto['getTestInjector'],
								annotations: []
							})
						],
						consumedMembers: [],
						annotations: []
					});
					return metaClass;
				});
				Object.defineProperty(TestController, '$di', {
					value: {
						fieldsInjectedTypes: [
							{
								name: 'spy',
								type: TestSpy
							},
							{
								name: 'executor',
								type: TestExecutor
							},
							{
								name: 'injector',
								type: N4Injector
							}
						]
					}
				});
			}
		};
	});
})(typeof module !== 'undefined' && module.exports ? require('n4js-node').System(require, module) : System);
//# sourceMappingURL=TestController.map
