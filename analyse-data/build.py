import os
from pybuilder.core import use_plugin, init, Author

use_plugin('filter_resources')

use_plugin('python.core')
use_plugin('python.unittest')
use_plugin('python.install_dependencies')
use_plugin('python.distutils')
use_plugin('python.pydev')
use_plugin('copy_resources')

authors = [
        Author('Arne Hilmann', 'arne.hilmann@gmail.com'),
        ]
name = 'bigpicture-analysedata'
summary = 'Creates bigpicture dot files '
description = 'Create a BigPicture of your server landscape - the analyse-data part'
license = 'proprietary'
version = '0.7'

default_task = ['verify', 'publish']


@init
def set_properties(project):
    project.depends_on('pygraphviz')

    project.get_property('distutils_commands').append('bdist_rpm')
    project.set_property('copy_resources_target', '$dir_dist')
    project.get_property('copy_resources_glob').append('setup.cfg')
    project.get_property('copy_resources_glob').append('lib/*')
    project.get_property('copy_resources_glob').append('cypher-query/*')

    for f in os.listdir('lib'):
        project.install_file('/usr/share/bigpicture/lib', os.path.join('lib', f))

    for f in os.listdir('cypher-query'):
        project.install_file('/usr/share/bigpicture/cypher-query', os.path.join('cypher-query',f))

@init(environments="teamcity")
def set_properties_for_teamcity(project):
    import os
    project.version = '%s-%s' % (project.version, os.environ.get('BUILD_NUMBER', 0))
    project.default_task = ['install_build_dependencies', 'package']
