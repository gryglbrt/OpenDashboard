/*******************************************************************************
 * Copyright 2015 Unicon (R) Licensed under the
 * Educational Community License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 *
 * http://www.osedu.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 *******************************************************************************/
(function(angular){
    'use strict';
    
    angular
    .module('OpenDashboard')
    .controller('CourseListController', function($log, $scope, $state, OpenDashboard_API, SessionService, TenantService, CourseDataService, ContextMappingService) {
      $scope.courses = null;
      $scope.tenant = null;
      var currentUser = SessionService.getCurrentUser();
      if (currentUser) {
        TenantService.getTenant(currentUser.tenant_id)
        .then(function (tenantData){
          $log.debug(tenantData);
          $scope.tenant = tenantData;
          var options = {};
          options.userId = currentUser.user_id;
          options.tenantId = currentUser.tenant_id;
          options.isLti = SessionService.isLTISession();
          
          CourseDataService.getContexts(options)
          .then(function(courseData){
            $log.debug(courseData);
            $scope.courses = courseData;
          });

        })
      }
      
      $scope.goToDashboard = function(tenant,course) {
    	
    	ContextMappingService.getWithTenantAndCourse(tenant.id,course.id)
    	.then(function(data){
    	  $log.log(data);
    	  if (!data) {
    		ContextMappingService.createWithTenantAndCourse(tenant.id,course.id)
    		.then(function(data) {
    		  var options = {};
    		  options['id'] = data.context;
    		  options['title'] = course.title;
    		  OpenDashboard_API.setCourse(options);
    		  $state.go('index',{"tenantId":tenant.id,"courseId":course.id});
    		});
    	  }
    	  else {
    	    var options = {};
    	    options['id'] = data.context;
    	    options['title'] = course.title;
    	    OpenDashboard_API.setCourse(options);
    		$state.go('index',{"tenantId":tenant.id,"courseId":course.id});
    	  }
    	});
    	  
        
      }
      
    });
})(angular);
